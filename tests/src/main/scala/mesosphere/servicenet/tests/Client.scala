package mesosphere.servicenet.tests

import java.net.InetSocketAddress

import com.github.theon.uri.Uri
import com.github.theon.uri.Uri._
import com.twitter.conversions.time.longToTimeableNumber
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{ RequestBuilder, Http }
import com.twitter.finagle.{ Service, SimpleFilter }
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.{ HttpResponse, HttpRequest }
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import play.api.libs.json.Json

import mesosphere.servicenet.util.{ Logging, Properties }
import com.twitter.conversions.storage.longToStorageUnitableWholeNumber

case class TestRequestResponse(requestNumber: Int,
                               responseServerIp: String)

class Client(hostname: String, port: Int) extends Logging {

  /**
    * Convert HTTP 4xx and 5xx class responses into Exceptions.
    */
  class HandleErrors extends SimpleFilter[HttpRequest, HttpResponse] {
    def apply(
      request: HttpRequest,
      service: Service[HttpRequest, HttpResponse]) = {
      service(request) flatMap { response =>
        response.getStatus match {
          case OK => Future.value(response)
          case _ => Future.exception(
            new Exception(response.getStatus.getReasonPhrase)
          )
        }
      }
    }
  }

  val address = new InetSocketAddress(hostname, port)

  lazy val builder = ClientBuilder()
    .codec(Http().maxResponseSize(10.megabytes))
    .hosts(address)
    .tcpConnectTimeout(2.seconds)
    .requestTimeout(15.seconds)
    .hostConnectionLimit(30)

  lazy val client = {
    val newClient = new HandleErrors andThen builder.build()
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() {
        newClient.close()
      }
    })
    newClient
  }

  private[this] def get(uri: Uri): HttpRequest = {
    val uriString = s"http://$hostname:$port/${uri.toString()}"
    RequestBuilder()
      .url(uriString.replaceAll("(?<!:)//", "/"))
      .buildGet()
  }

  def ping(requestNumber: Int = 0) = {
    client(get("/" ? ("requestNumber" -> requestNumber))) flatMap {
      case response =>
        Future.value(
          new TestRequestResponse(
            requestNumber,
            response.headers().get("ServerIP")
          )
        )
    }
  }
}

object Client extends Logging with BalanceFactorTestFormatters {
  def usage() = {
    System.err.println("Missing host arg.\n Usage: Client <hostname>[:port]")
    System.exit(2)
  }

  def main(args: Array[String]) = {
    val hostnamePort = {
      if (args.length == 0) None
      args(0).split(":") match {
        case Array(hostname, port) => Some(hostname -> port.toInt)
        case Array(hostname)       => Some(hostname -> 80)
        case _                     => None
      }
    }

    val requestCount = Properties.underlying.getOrElse(
      "svcnet.test.requests",
      "100"
    ).toInt
    val balanceVariance = Properties.underlying.getOrElse(
      "svcnet.test.balance.variance",
      "0.05"
    ).toDouble

    hostnamePort match {
      case Some(t) =>
        val (hostname, port) = t
        log.info(s"svcnet.test.requests = $requestCount")
        log.info(s"svcnet.test.balance.variance = $balanceVariance")
        log.info(s"clientConnection = $hostname:$port")

        val client = new Client(hostname, port)
        val results = new BalanceFactorTest(client).runBalanceFactorTest(
          requestCount,
          balanceVariance
        )
        println(Json.prettyPrint(Json.toJson(results)))
        if (results.pass) System.exit(0) else System.exit(5)
      case _ => usage()
    }
  }
}