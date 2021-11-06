package EShop.lab3

import EShop.lab2
import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK

  private final case class WrappedCartActorEvent(event: TypedCartActor.Event) extends Command
  private final case class WrappedCheckoutActorEvent(event: TypedCheckout.Event) extends Command
}

class OrderManager {

  import OrderManager._

  def start: Behavior[OrderManager.Command] = uninitialized

  def uninitialized: Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) => {
      msg match {
        case AddItem(id, sender) =>
          val cart = context.spawn(new TypedCartActor().start, "cart")
          cart ! TypedCartActor.AddItem(id)
          sender ! Done
          open(cart)
        case _ => Behaviors.same
      }
    }
  )

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) => {
      val cartEventMapper: ActorRef[TypedCartActor.Event] =
        context.messageAdapter(rsp => WrappedCartActorEvent(rsp))

      val checkoutEventMapper: ActorRef[TypedCheckout.Event] =
        context.messageAdapter(rsp => WrappedCheckoutActorEvent(rsp))

      msg match {
        case AddItem(id, sender) =>
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          Behaviors.same
        case RemoveItem(id, sender) =>
          cartActor ! TypedCartActor.RemoveItem(id)
          sender ! Done
          Behaviors.same
        case Buy(sender) =>
          cartActor ! TypedCartActor.StartCheckout(cartEventMapper, checkoutEventMapper)
          inCheckout(cartActor, sender)
        case _ =>
          Behaviors.same
      }
    }
  )

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case wrapped: WrappedCartActorEvent =>
          wrapped.event match {
            case TypedCartActor.CheckoutStarted(checkoutRef) =>
              senderRef ! Done
              inCheckoutWaitForUser(cartActorRef, checkoutRef)
            case _ => Behaviors.same
          }
        case _ =>
          Behaviors.same
      }
  )

  def inCheckoutWaitForUser(cartActorRef: ActorRef[TypedCartActor.Command], checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
          inPayment(cartActorRef, sender)
        case _ =>
          Behaviors.same
      }
  )

  def inPayment(cartActorRef: ActorRef[TypedCartActor.Command], senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
//        case ConfirmPaymentStarted(paymentRef) =>
//          senderRef ! Done
//          inPayment(cartActorRef, paymentRef, senderRef)
        case wrapped: WrappedCheckoutActorEvent =>
          wrapped.event match {
            case TypedCheckout.PaymentStarted(paymentRef) =>
              senderRef ! Done
              inPayment(cartActorRef, paymentRef, senderRef)
            case _ => Behaviors.same
          }
        case _ =>
          Behaviors.same
      }
  )

  def inPayment(cartActorRef: ActorRef[TypedCartActor.Command],
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case Pay(sender) =>
          paymentActorRef ! Payment.DoPayment
          sender ! Done
          Behaviors.same
        case ConfirmPaymentReceived =>
          finished(cartActorRef)
        case _ =>
          Behaviors.same
      }
  )

  def finished(cartActorRef: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case wrapped: WrappedCheckoutActorEvent =>
          wrapped.event match {
            case TypedCheckout.CheckOutClosed =>
              cartActorRef ! TypedCartActor.ConfirmCheckoutClosed
              Behaviors.stopped
            case _ => Behaviors.same
          }
        case _ =>
          Behaviors.same
      }
  )
}
