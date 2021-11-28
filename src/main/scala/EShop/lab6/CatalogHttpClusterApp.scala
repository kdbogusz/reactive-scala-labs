package EShop.lab6

import EShop.lab5.{ProductCatalog, SearchService}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import akka.http.scaladsl.server.Directives._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Try

class HttpCatalogNode(system: ActorSystem[Nothing]) {
  private val instancesPerNode = 3

  for (i <- 0 to instancesPerNode) system.systemActorOf(ProductCatalog(new SearchService()), s"catalog$i")

  def terminate(): Unit =
    system.terminate()
}

class HttpCounterNode(system: ActorSystem[Nothing]) {
  private val instancesPerNode = 1

  for (i <- 0 to instancesPerNode) system.systemActorOf(Counter(), s"counter$i")

  def terminate(): Unit =
    system.terminate()
}

object CatalogClusterNodeApp extends App {
  private val config = ConfigFactory.load()

  val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ClusterCatalogRouters",
    config
      .getConfig(Try(args(0)).getOrElse("seed-node1"))
      .withFallback(config.getConfig("cluster-default"))
  )

  val counterNode  = new HttpCounterNode(system)
  val catalogNodes = new HttpCatalogNode(system)

  Await.ready(system.whenTerminated, Duration.Inf)
}

object CatalogHttpClusterApp extends App {
  val catalogHttpServerInCluster = new CatalogHttpServerInCluster()
  catalogHttpServerInCluster.run(args(0).toInt)
}

class CatalogHttpServerInCluster() extends CatalogJsonSupport {
  private val config = ConfigFactory.load()

  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ClusterCatalogRouters",
    config.getConfig("cluster-default")
  )

  implicit val scheduler        = system.scheduler
  implicit val executionContext = system.executionContext

  val catalogs = system.systemActorOf(Routers.group(ProductCatalog.ProductCatalogServiceKey), "clusterCatalogRouter")

  val counter = system.systemActorOf(Routers.group(Counter.CounterServiceKey), "counterCluster")

  implicit val timeout: Timeout = 5.seconds

  def routes1: Route =
    path("productcatalog") {
      get {
        parameters("brand".as[String], "productKeyWords".as[String]) { (brand, productKeyWords) =>
          complete {
            catalogs
              .ask(replyTo => ProductCatalog.GetItems(brand, productKeyWords.split(" ").toList, replyTo))
              .mapTo[ProductCatalog.Items]
          }
        }
      }
    }

  def routes2: Route =
    path("counter") {
      get {
        complete {
          Future.successful(
            counter.ask(replyTo => RequestResults(replyTo)).mapTo[CounterResult]
          )
        }
      }
    }

  def routes = routes1 ~ routes2

  def run(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    println(s"Server now online.\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete { _ =>
        system.terminate()
      }
  }
}
