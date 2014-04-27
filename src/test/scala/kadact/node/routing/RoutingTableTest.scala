package kadact.node.routing

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import kadact.config.TestKadActConfig
import scala.concurrent.duration._
import akka.util.Timeout
import kadact.nodeForTest._
import kadact.node.routing.RoutingTable.{Insert, PickNNodesCloseTo}

class RoutingTableTest extends TestKit(ActorSystem("test", ConfigFactory.load("application-test")))
                               with WordSpecLike with MustMatchers with ImplicitSender with BeforeAndAfterAll
                               with TestKadActConfig {

  implicit val timeout = Timeout(3 seconds)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A RoutingTable actor" must {
    "be instantiable" in {
      newRoutingTable(0)
    }
    "return an empty set when no insert was ever done" in {
      val routingTable = newRoutingTable(0)
      routingTable ! PickNNodesCloseTo(config.k, 1)
      expectMsg(Set())
    }
    "return a contact that was inserted" in {
      val routingTable = newRoutingTable(0)
      val (_, contacts) = mockContacts(7)
      routingTable ! Insert(contacts(0))
      routingTable ! PickNNodesCloseTo(config.k, 1)
      expectMsg(Set(contacts(0)))
    }
    "return the closest contacts that it knows of" ignore {
      val routingTable = newRoutingTable(0)
      val ids = 7 to 9
      val (_, contacts) = mockContacts(ids: _*)
      for ((id, index) <- ids.zipWithIndex) {
        routingTable ! Insert(contacts(index))
      }
      routingTable ! PickNNodesCloseTo(config.k, 1)
      expectMsg(Set(contacts(0), contacts(1)))

      routingTable ! PickNNodesCloseTo(config.k, 8)
      expectMsg(Set(contacts(1), contacts(2)))
    }
  }

  def newRoutingTable(nodeId: Int = 0) = {
    system.actorOf(Props(new RoutingTable(nodeId)))
  }
}
