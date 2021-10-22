package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props, Timers}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor with Timers {

  import context._
  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      scheduleTimer
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
    case _ =>
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      timer.cancel()
      scheduleTimer
      context become nonEmpty(cart.addItem(item), scheduleTimer)
    case RemoveItem(item) if cart.contains(item) =>
      timer.cancel()
      if (cart.size == 1) {
        context become empty
      } else {
        scheduleTimer
        context become nonEmpty(cart.removeItem(item), scheduleTimer)
      }
    case StartCheckout =>
      timer.cancel()
      context become inCheckout(cart)
    case ExpireCart =>
      context become empty
    case _ =>
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      scheduleTimer
      context become nonEmpty(cart, scheduleTimer)
    case ConfirmCheckoutClosed =>
      context become empty
    case _ =>
  }

}
