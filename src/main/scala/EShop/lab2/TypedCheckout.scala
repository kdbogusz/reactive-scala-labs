package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {
  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command
  case object PaymentRejected                                                                extends Command
  case object PaymentRestarted                                                               extends Command

  sealed trait Event
  case object CheckOutClosed                                    extends Event
  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                   extends Event
  case object CheckoutCancelled                                 extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))
}

class TypedCheckout(
                     cartActor: Option[ActorRef[TypedCartActor.Command]] = None,
                     checkoutEventMapper: Option[ActorRef[TypedCheckout.Event]] = None
                   ) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case StartCheckout =>
          def timer: Cancellable = context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)
          selectingDelivery(timer)
        case _ =>
          Behaviors.same
      }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case ExpireCheckout =>
          cancelled
        case SelectDeliveryMethod(method) =>
          timer.cancel()
          def newTimer: Cancellable = context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)
          selectingPaymentMethod(newTimer)
        case CancelCheckout =>
          cancelled
        case _ =>
          Behaviors.same
      }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case ExpireCheckout =>
          cancelled
        case SelectPayment(payment, orderManagerRef) =>
          checkoutEventMapper match {
            case None =>
              def timer: Cancellable = context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)
              orderManagerRef ! OrderManager.ConfirmPaymentStarted(context.spawn(new Payment(payment, orderManagerRef, context.self).start, "payment"))
              processingPayment(timer)
            case Some(mapper) =>
              def timer: Cancellable = context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)
              mapper ! PaymentStarted(context.spawn(new Payment(payment, orderManagerRef, context.self).start, "payment"))
              processingPayment(timer)
          }
        case CancelCheckout =>
          cancelled
        case _ =>
          Behaviors.same
      }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case ConfirmPaymentReceived =>
          checkoutEventMapper match {
            case None =>
              timer.cancel()
              cartActor.get ! TypedCartActor.ConfirmCheckoutClosed
              closed
            case Some(mapper) =>
              timer.cancel()
              mapper ! CheckOutClosed
              closed
          }
        case ExpirePayment =>
          cancelled
        case CancelCheckout =>
          cancelled
        case _ => Behaviors.same
      }
  )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case _ => Behaviors.stopped
      }
  )

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case _ => Behaviors.stopped
      }
  )

}