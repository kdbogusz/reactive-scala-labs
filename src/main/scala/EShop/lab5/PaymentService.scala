package EShop.lab5

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object PaymentService {

  sealed trait Response
  case object PaymentSucceeded extends Response

  case class PaymentClientError() extends Exception
  case class PaymentServerError() extends Exception

  val stopStrategy    = SupervisorStrategy.stop
  val restartStrategy = SupervisorStrategy.restart.withLimit(maxNrOfRetries = 3, withinTimeRange = 1.second)

  def apply(
    method: String,
    payment: ActorRef[Response]
  ): Behavior[HttpResponse] =
    Behaviors
      .supervise(
        Behaviors
          .supervise(apply2(method, payment))
          .onFailure[PaymentServerError](restartStrategy)
      )
      .onFailure[PaymentClientError](stopStrategy)

  // actor behavior which needs to be supervised
  // use akka.http.scaladsl.Http to make http based payment request
  // use getUri method to obtain url
  def apply2(
    method: String,
    payment: ActorRef[Response]
  ): Behavior[HttpResponse] = Behaviors.setup { context =>
    implicit val system           = context.system
    implicit val executionContext = system.executionContext
    val result                    = Http().singleRequest(HttpRequest(uri = getURI(method)))

    context.pipeToSelf(result) {
      case Success(value) => value
      case Failure(e)     => throw e
    }

    Behaviors.receiveMessage {
      case HttpResponse(StatusCodes.OK, _, _, _) =>
        payment ! PaymentSucceeded
        Behaviors.stopped
      case HttpResponse(StatusCodes.NotFound, _, _, _) =>
        throw PaymentClientError()
      case HttpResponse(StatusCodes.RequestTimeout, _, _, _) =>
        throw PaymentServerError()
      case HttpResponse(StatusCodes.ImATeapot, _, _, _) =>
        throw PaymentServerError()
      case _ =>
        Behaviors.same
    }
  }

  // remember running PymentServiceServer() before trying payu based payments
  private def getURI(method: String) = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }
}
