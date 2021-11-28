package EShop.lab5

import EShop.lab5.ProductCatalog.GetItems
import EShop.lab5.ReceptionistQuestion.FindCatalog
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object ProductCatalogServer {}

trait ProductCatalogJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {}

object ProductCatalogServerApp extends App {
  new ProductCatalogServer().start(9000)
}

object ReceptionistQuestion {
  sealed trait Command
  case class FindCatalog(brand: String, productKeyWords: List[String]) extends Command
  private case class ListingResponse(listing: Receptionist.Listing)    extends Command
  private case class ItemsResponse(items: ProductCatalog.Items)        extends Command

  var items: Option[ProductCatalog.Items]   = None
  var brand: Option[String]                 = None
  var productKeyWords: Option[List[String]] = None

  def apply(): Behavior[Command] = {
    Behaviors.setup[Command] { context =>
      val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse.apply)
      val itemsResponseAdapter   = context.messageAdapter[ProductCatalog.Items](ItemsResponse.apply)

      Behaviors.receiveMessagePartial {
        case FindCatalog(brand, productKeyWords) =>
          this.brand = Some(brand)
          this.productKeyWords = Some(productKeyWords)
          context.system.receptionist ! Receptionist.Find(
            ProductCatalog.ProductCatalogServiceKey,
            listingResponseAdapter
          )
          Behaviors.same
        case ListingResponse(ProductCatalog.ProductCatalogServiceKey.Listing(listings)) =>
          listings.foreach(
            listing => listing ! GetItems(brand.get, productKeyWords.get, itemsResponseAdapter.unsafeUpcast)
          )
          Behaviors.same
        case ItemsResponse(items) =>
          this.items = Some(items)
          Behaviors.same
      }
    }
  }
}

class ProductCatalogServer extends ProductCatalogJsonSupport {
  implicit val system: ActorSystem[ReceptionistQuestion.Command] =
    ActorSystem(ReceptionistQuestion.apply(), "ProductCatalog")

  def routes: Route = {

    path("productcatalog") {
      get {
        parameters("brand".as[String], "productKeyWords".as[String]) { (brand, productKeyWords) =>
          system ! FindCatalog(brand, productKeyWords.split(" ").toList)
          Thread.sleep(5000)
          val items = ReceptionistQuestion.items
          if (items.isEmpty) {
            complete {
              Future.successful("[]")
            }
          } else {
            complete {
              var result = "["
              items.foreach(
                itemList =>
                  itemList.items.foreach(
                    item =>
                      result = result ++ ("{\"id\":\"" ++ item.id.toString ++ "\",\"name\":\"" ++ item.name ++ "\",\"brand\":\"" ++ item.brand ++ "\",\"price\":" ++ item.price
                        .toString() ++ ",\"count\":" ++ item.count.toString ++ "},")
                )
              )
              result = result.substring(0, result.length - 1)
              result = result ++ "]"
              Future.successful(result)
            }
          }
        }
      }
    }
  }

  def start(port: Int) = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.ready(system.whenTerminated, Duration.Inf)
  }

}
