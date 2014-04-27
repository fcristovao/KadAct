package kadact.node.routing

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import kadact.config.TestKadActConfig
import scala.concurrent.duration._
import akka.util.Timeout

class RoutingTableTest extends TestKit(ActorSystem("test", ConfigFactory.load("application-test")))
                               with WordSpecLike with MustMatchers with ImplicitSender with BeforeAndAfterAll {

  implicit val config = TestKadActConfig()
  implicit val timeout = Timeout(3 seconds)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A RoutingTable actor" must {
    "be instantiable" in {
      system.actorOf(Props(new RoutingTable(0)))
    }

  }

}
