package EShop.lab6

import EShop.lab5.ProductCatalog.Item
import EShop.lab5.{ProductCatalog, SearchService}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Try

trait CatalogJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  case class SearchDTO(brand: String, productKeyWords: String)

  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)

    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val catalogDTOSearch = jsonFormat2(SearchDTO)
  implicit val itemFormat       = jsonFormat5(ProductCatalog.Item)
  implicit val catalogResponse  = jsonFormat1(ProductCatalog.Items)
  implicit val counterResponse  = jsonFormat1(CounterResult)
}

object CatalogHttpApp extends App {
  val catalogHttpServer = new CatalogHttpServer()
  catalogHttpServer.run(Try(args(0).toInt).getOrElse(9000))
}

class CatalogHttpServer extends CatalogJsonSupport {

  implicit val system           = ActorSystem(Behaviors.empty, "ReactiveRouters")
  implicit val scheduler        = system.scheduler
  implicit val executionContext = system.executionContext
  val catalogs                  = system.systemActorOf(Routers.pool(5)(ProductCatalog(new SearchService())), "catalogRouter")

  implicit val timeout: Timeout = 5.seconds

  def routes: Route =
    path("productcatalog") {
      get {
        parameters("brand".as[String], "productKeyWords".as[String]) { (brand, productKeyWords) =>
          complete {
            Future.successful(
              catalogs
                .ask(replyTo => ProductCatalog.GetItems(brand, productKeyWords.split(" ").toList, replyTo))
                .mapTo[ProductCatalog.Items]
            )
          }
        }
      }
    }

  def run(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    println(s"Server now online. Press RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
