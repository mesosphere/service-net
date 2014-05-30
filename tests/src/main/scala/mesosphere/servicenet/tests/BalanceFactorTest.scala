package mesosphere.servicenet.tests

import com.twitter.util.{ Await, Future, Stopwatch }
import java.net.InetSocketAddress

import play.api.libs.json._
import scala.Some

import mesosphere.servicenet.util.Logging

case class BalanceFactorTestResults(clientConnection: InetSocketAddress,
                                    requestCount: Int,
                                    expectedBalanceFactor: Double,
                                    balanceVariance: Double,
                                    durationMillis: Long,
                                    pass: Boolean,
                                    failureException: Option[Throwable],
                                    allResults: List[ServerRequestSummary],
                                    unbalanced: List[ServerRequestSummary])

case class ServerRequestSummary(serverIp: String,
                                numRequests: Int,
                                percentage: Double)

class BalanceFactorTest(client: Client) extends Logging {
  def runBalanceFactorTest(
    requestCount: Int,
    expectBalanceFactorDelta: Double): BalanceFactorTestResults = {
    val stopwatch = Stopwatch.start()

    var possibleError: Option[Throwable] = None
    val f = {
      Future.collect(
        1 to requestCount map {
          i => client.ping(i)
        }
      ) onFailure {
          case t: Throwable =>
            log.error("Error", t)
            possibleError = Some(t)
        }
    }

    val resp = Await.result(f)

    assert(
      resp.size == requestCount,
      s"expected: $requestCount actual: ${resp.size}"
    )
    val totalRequests: Double = resp.size

    val results = resp.groupBy { case response => response.responseServerIp }
      .toList
      .map {
        case g =>
          val (serverIp, responses) = g
          ServerRequestSummary(
            serverIp,
            responses.size,
            responses.size / totalRequests
          )
      }

    val expectBalanceFactor = 1d / results.size
    val expectBalanceFactorMin = expectBalanceFactor - expectBalanceFactorDelta
    val expectBalanceFactorMax = expectBalanceFactor + expectBalanceFactorDelta
    val unbalanced = results.filterNot {
      case result =>
        expectBalanceFactorMin <= result.percentage &&
          result.percentage <= expectBalanceFactorMax
    }

    new BalanceFactorTestResults(
      client.address,
      requestCount,
      expectBalanceFactor,
      expectBalanceFactorDelta,
      durationMillis = stopwatch().inMillis,
      pass = unbalanced.isEmpty,
      failureException = possibleError,
      allResults = results,
      unbalanced = unbalanced
    )
  }
}

trait BalanceFactorTestFormatters {

  implicit val seqServerRequestSummaryFormat =
    new Format[Seq[ServerRequestSummary]] {
      override def reads(json: JsValue): JsResult[Seq[ServerRequestSummary]] = {
        ???
      }

      override def writes(o: Seq[ServerRequestSummary]): JsValue = {
        JsObject(o.map {
          case srs: ServerRequestSummary =>
            srs.serverIp -> JsNumber(srs.numRequests)
        })
      }
    }

  implicit val balanceFactorTestResultsFormat =
    new Format[BalanceFactorTestResults] {
      override def reads(json: JsValue): JsResult[BalanceFactorTestResults] = {
        ???
      }

      override def writes(o: BalanceFactorTestResults): JsValue = {
        val conn = o.clientConnection
        JsObject(Seq(
          "connection" -> JsString(s"${conn.getHostName}:${conn.getPort}"),
          "resolvedAddress" -> JsString(conn.getAddress.getHostAddress),
          "requestCount" -> JsNumber(o.requestCount),
          "expectedBalanceFactor" -> JsNumber(o.expectedBalanceFactor),
          "balanceVariance" -> JsNumber(o.balanceVariance),
          "durationMillis" -> JsNumber(o.durationMillis),
          "testPassed" -> JsBoolean(o.pass),
          "results" -> seqServerRequestSummaryFormat.writes(o.allResults),
          "unbalanced" -> seqServerRequestSummaryFormat.writes(o.unbalanced)
        ))
      }
    }
}