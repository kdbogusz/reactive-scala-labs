package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable = context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      Empty,
      commandHandler(context),
      eventHandler(context)
    )
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))
          case GetItems(sender) =>
            sender ! state.cart
            Effect.none
          case _ =>
            Effect.none
        }

      case NonEmpty(cart, _) =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))
          case GetItems(sender) =>
            sender ! state.cart
            Effect.none
          case RemoveItem(item) if cart.contains(item) && cart.size == 1 =>
            Effect.persist(CartEmptied)
          case RemoveItem(item) if cart.contains(item) =>
            Effect.persist(ItemRemoved(item))
          case ExpireCart =>
            Effect.persist(CartExpired)
          case StartCheckoutOld(orderManagerRef) =>
            val checkout = context.spawn(new TypedCheckout(Some(context.self)).start, "checkout")
            checkout ! TypedCheckout.StartCheckout
            orderManagerRef ! OrderManager.ConfirmCheckoutStarted(checkout)
            Effect.persist(CheckoutStarted(checkout))
          case _ =>
            Effect.none
        }

      case InCheckout(_) =>
        command match {
          case GetItems(sender) =>
            sender ! state.cart
            Effect.none
          case ConfirmCheckoutCancelled =>
            Effect.persist(CheckoutCancelled)
          case ConfirmCheckoutClosed =>
            Effect.persist(CheckoutClosed)
          case _ =>
            Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    event match {
      case CheckoutStarted(_)        => InCheckout(state.cart)
      case ItemAdded(item)           => NonEmpty(state.cart.addItem(item), scheduleTimer(context))
      case ItemRemoved(item)         => NonEmpty(state.cart.removeItem(item), scheduleTimer(context))
      case CartEmptied | CartExpired => Empty
      case CheckoutClosed            => Empty
      case CheckoutCancelled         => NonEmpty(state.cart, scheduleTimer(context))
    }
  }

}
