package EShop.lab6

import io.gatling.core.Predef.{Simulation, jsonFile, rampUsers, scenario, _}
import io.gatling.http.Predef.http

import scala.concurrent.duration._

class HttpCatalogGatlingTest extends Simulation {

  val httpProtocol = http
//    .baseUrls("http://localhost:9001", "http://localhost:9002", "http://localhost:9003")
    .baseUrls("http://localhost:9000")
    .acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn = scenario("BasicSimulation")
    .feed(jsonFile("C:/Users/nuttard/repos/reactive-scala-labs/target/scala-2.13/it-classes/data/catalog_data.json").random)
    .exec(
      http("catalog_basic")
        .get("/productcatalog?brand=${brand}&productKeyWords=${productKeyWords}")
        .asJson
    )
    .pause(5)

  setUp(
    scn.inject(rampUsers(12000).during(1.minutes))
  ).protocols(httpProtocol)
}