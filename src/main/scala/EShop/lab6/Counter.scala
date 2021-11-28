package EShop.lab6

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.pubsub.Topic
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, Behavior}

sealed trait CounterMessage
case class CounterResult(counter: Int)                      extends CounterMessage
case class RequestResults(replyTo: ActorRef[CounterResult]) extends CounterMessage
case object IncrementCounter                                extends CounterMessage

object CounterTopic {
  def apply(): Behavior[Topic.Command[CounterMessage]] =
    Topic[CounterMessage]("counter")
}

object Counter {
  val CounterServiceKey = ServiceKey[CounterMessage]("Counter")

  def apply(): Behavior[CounterMessage] = Behaviors.setup { context =>
    context.system.receptionist ! Receptionist.register(CounterServiceKey, context.self)

    val topic = context.spawn(CounterTopic(), "CounterTopic")
    val adapter = context.messageAdapter[CounterMessage] {
      case IncrementCounter => IncrementCounter
    }

    var counter = 0
    topic ! Topic.Subscribe(adapter)

    Behaviors.receiveMessage {
      case IncrementCounter =>
        counter += 1
        Behaviors.same
      case RequestResults(replyTo) =>
        replyTo ! CounterResult(counter)
        Behaviors.same
    }
  }
}
