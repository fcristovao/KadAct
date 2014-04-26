package kadact.node

import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import org.scalatest._
import kadact.config.TestKadActConfig
import com.typesafe.config.ConfigFactory
import akka.actor.{ActorRef, Props, ActorSystem}
import kadact.node.KadActNode._
import kadact.config.modules.{LookupManagerModule, RoutingTableModule}
import kadact.node.KadActNode.AddToNetwork
import akka.util.Timeout
import scala.concurrent.duration._


class KadActNodeTest extends TestKit(ActorSystem("test", ConfigFactory.load("application-test")))
                             with WordSpecLike with MustMatchers with ImplicitSender with BeforeAndAfterAll {

  implicit val config = TestKadActConfig()
  implicit val injector = new RoutingTableModule :: new LookupManagerModule
  implicit val timeout = Timeout(3 seconds)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit def intToKey(int: Int) : Key = BigInt(int)

  "An KadActNode actor" when {
    "alone in the network" must {
      "be able to start" in {
        start(newKadActNode())
      }
      "return the Contact with the correct nodeId" in {
        val nodeFSM = start(newKadActNode(0))
        nodeFSM ! GetContact
        expectMsg(Contact(BigInt(0),nodeFSM))
      }
      "always answer its contact" in {
        val nodeFSM = system.actorOf(Props(new KadActNode[Int]()))
        nodeFSM ! GetContact
        expectMsgClass(classOf[Contact])
        nodeFSM ! Start
        expectMsg(Done)
        nodeFSM ! GetContact
        expectMsgClass(classOf[Contact])
      }
      "be able to store values" in {
        val nodeFSM = start(newKadActNode())
        val key: Key = BigInt(1)

        nodeFSM ! AddToNetwork(key, 10)
        expectMsg(Done)
      }
      "get previously stored values" in {
        val nodeFSM = start(newKadActNode())
        val key: Key = BigInt(1)

        nodeFSM ! AddToNetwork(key, 10)
        expectMsg(Done)

        nodeFSM ! GetFromNetwork(key)
        expectMsg(Some(10))
      }
      "not get a value that was never inserted" in {
        val nodeFSM = start(newKadActNode())
        val key: Key = BigInt(1)

        nodeFSM ! GetFromNetwork(key)
        expectMsg(None)
      }
    }
    "with one other node in the network" must {
      "be able to join it" in {
        createTwoNodeKadActNetwork()
      }
      "not get a value that was never inserted" in {
        val (first, second) =createTwoNodeKadActNetwork()
        val key: Key = 1

        first.node ! GetFromNetwork(key)
        expectMsg(None)

        second.node ! GetFromNetwork(key)
        expectMsg(None)
      }
      "handle the FindNode protocol message" in {
        val (first, second) =createTwoNodeKadActNetwork()
        first.node ! kadact.messages.FindNode(second, 0, 0) // Look for key 0 in node 0
        expectMsg(kadact.messages.FindNodeResponse(first, 0, Set(first)))
        //^- "The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor."
      }
      "get previously stored values when requested to the same node" in {
        val (first, _) = createTwoNodeKadActNetwork()
        val key: Key = 1

        first.node ! AddToNetwork(key, 10)
        expectMsg(Done)

        first.node ! GetFromNetwork(key)
        expectMsg(Some(10))
      }
      "get previously stored values when requested to another node" in {
        val (first, second) = createTwoNodeKadActNetwork()
        val key: Key = 1

        first.node ! AddToNetwork(key, 10)
        expectMsg(Done)

        second.node ! GetFromNetwork(key)
        expectMsg(Some(10))
      }
    }
  }

  def start(node: ActorRef) = {
    node ! Start
    expectMsg(Done)
    node
  }


  private def newKadActNode(nodeId: Int = 0): ActorRef = {
    system.actorOf(Props(new KadActNode[Int](BigInt(nodeId))))
  }

  def createTwoNodeKadActNetwork() = {
    import akka.pattern.{ask, pipe}
    import system.dispatcher
    val original = start(newKadActNode())
    val joining = newKadActNode(15) // the farthest away

    (original ? GetContact).mapTo[Contact].map(Join(_)) pipeTo joining

    expectMsg(Done)
    (Contact(BigInt(0), original), Contact(BigInt(15), joining))
  }


}
