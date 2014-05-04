package kadact.node.lookup

import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import kadact.config.TestKadActConfig
import akka.util.Timeout
import scala.concurrent.duration._
import kadact.node.Contact
import kadact.node.routing.RoutingTable.PickNNodesCloseTo
import kadact.nodeForTest._
import kadact.node.lookup.LookupManager.{LookupNodeResponse, LookupNode}

class NodeLookupTest extends TestKit(ActorSystem("test", ConfigFactory.load("application-test")))
                             with WordSpecLike
                             with MustMatchers
                             with ImplicitSender
                             with BeforeAndAfterAll
                             with TestKadActConfig {

  implicit val timeout = Timeout(3 seconds)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val mockKadActNode = TestProbe()
  val mockRoutingTable = TestProbe()
  val generation = 0

  "A NodeLookup actor" when {
    "there's only one node in the network" must {
      "return the only node existing in the network" in {
        // The only KadActNode in the network is the one that we are working for
        val nodeId = BigInt(0)
        val contact = Contact(nodeId, mockKadActNode.ref)
        val nodeLookup = system.actorOf(Props(new NodeLookup(contact, mockRoutingTable.ref, generation)))
        nodeLookup ! LookupNode(nodeId)
        mockRoutingTable.expectMsg(PickNNodesCloseTo(config.alpha, nodeId))
        mockRoutingTable.reply(Set[Contact]()) // The routing table should never contain the node it works for

        expectMsg(LookupNodeResponse(nodeId, Set[Contact](contact)))
      }
    }
    "there's two nodes in the network" must {
      "return the two (<= alpha) existing nodes in the network" in {
        /* Two nodes, Ids 0 and 8 */
        val (probes, contacts) = mockContacts(0, 8)

        val nodeLookup = system.actorOf(Props(new NodeLookup(contacts(0), mockRoutingTable.ref, generation)))
        val nodeId = BigInt(0)

        nodeLookup ! LookupNode(nodeId)

        mockRoutingTable.expectMsg(PickNNodesCloseTo(config.alpha, nodeId))
        mockRoutingTable.reply(Set[Contact](contacts(1)))

        probes(1).expectMsg(kadact.messages.FindNode(contacts(0), generation, nodeId))
        probes(1).reply(
          kadact.messages.FindNodeResponse(contacts(1), generation, Set(contacts(1)))
        ) //returns itself as the only contact

        expectMsg(LookupNodeResponse(nodeId, Set[Contact](contacts(0), contacts(1))))
      }
    }
  }

}
