package mesosphere.servicenet.ns

import java.lang.reflect.Field
import java.net.{ InetSocketAddress }
import scala.concurrent.duration._
import scala.util.{ Try, Success, Failure }

import akka.actor._
import akka.pattern.ask
import akka.io.IO
import akka.util.Timeout
import com.github.mkroli.dns4s
import com.github.mkroli.dns4s.akka.Dns
import com.github.mkroli.dns4s.dsl._
import com.github.mkroli.dns4s.section.{ ResourceRecord => RR }
import com.github.mkroli.dns4s.section.resource.{ PTRResource, AAAAResource }
import org.xbill.DNS

import mesosphere.servicenet.config.Config
import mesosphere.servicenet.dsl.{ AAAA, DNS, Doc, Diff }
import mesosphere.servicenet.util.{ InetAddressHelper, Logging }

/**
  * A simple DNS server
  *
  * See: http://www.ietf.org/rfc/rfc1035.txt
  *
  * Serves AAAA records present in the underlying
  * `mesosphere.servicenet.dsl.Doc`, and delegates to the host for all other
  * queries.
  */
class NameServer()(implicit val config: Config = Config()) extends Logging {

  implicit val system = ActorSystem("NameServer")
  implicit val timeout = Timeout(5.seconds)
  implicit val executionContext = system.dispatcher

  protected[this] var networkDoc: Doc = Doc()
  protected[this] var forward: Map[String, Seq[DNS]] = Map()
  protected[this] var reverse: Map[String, DNS] = Map()

  // See: http://www.dnsjava.org/doc/org/xbill/DNS/ResolverConfig.html
  protected lazy val underlying: DNS.SimpleResolver = {
    // We filter the resolvers if we are listening on port 53 because we don't
    // want to delegate DNS requests to ourselves!
    val fromSystem = DNS.ResolverConfig.getCurrentConfig().servers()
    val namesOfLocalhost = Set("::1", "localhost", "127.0.0.1")
    val servers =
      if (config.nsPort != 53) fromSystem
      else fromSystem.filterNot(namesOfLocalhost.contains(_))
    require(servers.size > 0,
      "There must be at least one valid fallback DNS server")
    new DNS.SimpleResolver(servers(0))
  }

  def resolve(query: dns4s.Message): Option[dns4s.Message] = {
    def fmt(s: String) = Formatter.format(query) + " // " + s
    query match {
      case Query(_) ~ Questions(QName(name) ~ TypeAAAA() :: Nil) =>
        log info fmt("Resolving")
        val answers: Seq[RR] = forward.getOrElse(name, Nil).collect {
          case r: AAAA => r.addrs filter {
            address => !r.localize || config.instanceSubnet.contains(address)
          } map { address =>
            RR(
              `class` = RR.`classIN`,
              name = r.label,
              rdata = AAAAResource(address),
              ttl = r.ttl,
              `type` = RR.`typeAAAA`
            )
          }
        }.flatten
        if (answers.nonEmpty)
          Some(Response ~ Questions(query.question: _*) ~ Answers(answers: _*))
        else
          None

      // This case is here to speed up the common A, then AAAA query pattern.
      case Query(_) ~ Questions(QName(name) ~ TypeA() :: Nil) =>
        log info fmt("Resolving")
        val matchingAAAA: Seq[AAAA] = forward.getOrElse(name, Nil).collect {
          case r: AAAA => r
        }
        if (matchingAAAA.nonEmpty) {
          log info fmt("Returning NXDOMAIN for A to trigger AAAA")
          Some(Response ~ NameError ~ Questions(query.question: _*) ~ Answers())
        }
        else
          None

      case Query(_) ~ Questions(QName(name) ~ TypePTR() :: Nil) =>
        log info fmt("Resolving")
        reverse.get(name).map { dns =>
          val answer: RR = RR(
            `class` = RR.`classIN`,
            name = name,
            rdata = PTRResource(dns.label),
            ttl = dns.ttl,
            `type` = RR.`typePTR`
          )
          Response ~ Questions(query.question: _*) ~ Answers(answer)
        }

      case _ =>
        log debug fmt("Unhandled record type")
        None
    }
  }

  def delegate(query: dns4s.Message): Option[dns4s.Message] =
    Try {
      def fmt(s: String) = Formatter.format(query) + " // " + s
      val resolverAddress: InetSocketAddress = try {
        val f: Field = underlying.getClass().getDeclaredField("address")
        f.setAccessible(true)
        f.get(underlying).asInstanceOf[InetSocketAddress]
      }
      catch {
        case e: Throwable => {
          log error s"Failed to read resolver address: $e"
          throw e
        }
      }
      val formattedResolver = resolverAddress.getAddress.getHostAddress + {
        val p = resolverAddress.getPort
        if (p == 53) "" else ":" + p.toString
      }
      log info fmt(s"Delegating to $formattedResolver")
      val buffer: dns4s.MessageBuffer = query.apply().flipped
      val msg = new DNS.Message(buffer.getBytes(buffer.remaining).toArray)
      log debug fmt(s"Sending message to upstream name server:\n$msg")
      val response = underlying send msg
      log debug fmt(s"Received response from upstream name server:\n$response")
      toDns4s(response)
    }.toOption

  /**
    * Updates this name server with a new description of the underlying network.
    */
  def update(doc: Doc): Unit = synchronized {
    networkDoc = doc
    forward = doc.dns.groupBy(_.label.replaceAll("[.]$", ""))
    reverse = doc.dns.collect {
      case aaaa: AAAA => aaaa.addrs.map(InetAddressHelper.arpa(_) -> aaaa)
    }.flatten.toMap
  }

  /**
    * Updates this name server with changes to the underlying network.
    */
  def update(diff: Diff): Unit = synchronized { update(diff(networkDoc)) }

  class NameServerActor extends Actor {
    override def receive = {
      case query: dns4s.Message =>
        def fmt(s: String) = Formatter.format(query) + " // " + s
        resolve(query) orElse delegate(query) match {
          case Some(answer) => sender ! answer
          case None         => log debug fmt("No result")
        }
    }
  }

  def run(port: Int = config.nsPort): Unit = {
    val nsHandler = system actorOf Props(new NameServerActor)
    IO(Dns) ? Dns.Bind(nsHandler, port) onComplete {
      case Success(bound) => log.info(s"Bound port [$port]")
      case Failure(cause) => {
        log.error(s"Failed to bind port [$port]", cause)
        system.shutdown
        sys.exit(1)
      }
    }
  }

  protected def toDns4s(msg: DNS.Message): dns4s.Message =
    dns4s.Message(dns4s.MessageBuffer().put(msg.toWire).flipped)
}

/**
  * Companion object for [[NameServer]].
  */
object NameServer extends App {
  val ns = new NameServer

  val testDoc = {
    val loopbackAddress =
      InetAddressHelper.ipv6("0000 0000 0000 0000 0000 0000 0000 0001")

    val anotherAddress =
      InetAddressHelper.ipv6("fc75:0000:0000:0000:0000:9fb2:0000:0804")

    Doc(
      interfaces = Nil,
      dns = Seq(
        AAAA("foo.bar", Seq(loopbackAddress)),
        AAAA("foo.bar", Seq(anotherAddress))
      ),
      natFans = Nil,
      tunnels = Nil
    )
  }

  ns update testDoc
  ns.run()
}
