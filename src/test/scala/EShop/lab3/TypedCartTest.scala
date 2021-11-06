package EShop.lab3

import EShop.lab2
import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.Effect.SpawnedAnonymous
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import akka.testkit.TestActor.Message
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val inbox = TestInbox[Cart]()
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    testKit.run(TypedCartActor.AddItem(123))
    testKit.run(TypedCartActor.GetItems(inbox.ref))
    inbox.expectMessage(Cart.apply(Seq(123)))
  }

  it should "be empty after adding and removing the same item" in {
    val cartActor = testKit.spawn(new TypedCartActor().start, "cartActor1")
    val cart = testKit.createTestProbe[Cart]()
    cartActor ! TypedCartActor.AddItem(123)
    cartActor ! TypedCartActor.RemoveItem(123)
    cartActor ! TypedCartActor.GetItems(cart.ref)
    cart.expectMessage(Cart.empty)
  }

  it should "start checkout" in {
    val cartActor = testKit.spawn(new TypedCartActor().start, "cartActor2")
    val orderManager = testKit.createTestProbe[OrderManager.Command]()
    cartActor ! TypedCartActor.AddItem(123)
    cartActor ! TypedCartActor.StartCheckoutOld(orderManager.ref)
    orderManager.expectMessageType[OrderManager.ConfirmCheckoutStarted]
  }
}
