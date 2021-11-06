package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import TypedCheckout._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    //ConfirmCheckoutClosed
    val cartActor = testKit.spawn(new TypedCartActor().start, "cartActor3")
    val checkoutProbe = testKit.createTestProbe[TypedCheckout]()
    val orderManager = testKit.createTestProbe[OrderManager.Command]()
    cartActor ! TypedCartActor.AddItem(123)
    cartActor ! TypedCartActor.StartCheckoutOld(orderManager.ref)
    val msg = orderManager.receiveMessage()
    val checkout = msg.asInstanceOf[OrderManager.ConfirmCheckoutStarted].checkoutRef
    checkout ! TypedCheckout.SelectDeliveryMethod("letter")
    checkout ! TypedCheckout.SelectPayment("paypal", orderManager.ref)
    checkout ! TypedCheckout.ConfirmPaymentReceived
    checkoutProbe.expectNoMessage()  // needed for test to work; effectively forced wait
    val cart = testKit.createTestProbe[Cart]()
    cartActor ! TypedCartActor.GetItems(cart.ref)
    cart.expectMessage(Cart.empty)
  }

}
