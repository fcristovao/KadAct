package kadact.node

import akka.testkit.{EventFilter, TestFSMRef, ImplicitSender, TestKit}
import org.scalatest._
import kadact.config.TestKadActConfig
import com.typesafe.config.ConfigFactory
import akka.actor._
import kadact.node.KadActNode._
import kadact.config.modules.{LookupManagerModule, RoutingTableModule}
import kadact.node.KadActNode.AddToNetwork
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import kadact.node.KadActNode.InvalidKey
import kadact.node.KadActNode.Join
import kadact.node.KadActNode.AddToNetwork
import kadact.node.KadActNode.GetFromNetwork
import scala.Some
import kadact.node.KadActNode.Error
import kadact.node.Contact


class KadActNodeTest extends TestKit(ActorSystem("test", ConfigFactory.load("application-test")))
                             with WordSpecLike with MustMatchers with ImplicitSender with BeforeAndAfterAll {

  implicit val config = TestKadActConfig()
  implicit val injector = new RoutingTableModule :: new LookupManagerModule
  implicit val timeout = Timeout(3 seconds)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit def intToKey(int: Int): Key = BigInt(int)

  "A KadActNode actor" must {
    "be able to start" in {
      start(newKadActNode())
    }
    "return the Contact with the correct nodeId" in {
      val nodeFSM = start(newKadActNode(0))
      nodeFSM ! GetContact
      expectMsg(Contact(BigInt(0), nodeFSM))
    }
    "always answer its contact" in {
      val nodeFSM = newKadActNode(0)
      nodeFSM ! GetContact
      expectMsgClass(classOf[Contact])
      nodeFSM ! Start
      expectMsg(Done)
      nodeFSM ! GetContact
      expectMsgClass(classOf[Contact])
    }
    "refuse to store a key that it's outside of its space" in {
      val nodeFSM = start(newKadActNode())
      val key: Key = 16 // The testKadActConfig has b=4 which means 15 is the biggest key allowed

      nodeFSM ! AddToNetwork(key, 10)
      expectMsg(Error(InvalidKey(key)))
    }
    "refuse to be created with an invalid Id" in {
      EventFilter[ActorInitializationException](occurrences = 1) intercept {
        newKadActNode(16) // The testKadActConfig has b=4 which means 15 is the biggest key allowed
      }
    }
  }

  "A KadActNode actor" when {
    "alone in the network" must {
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
        createKadActNetwork(0, 15)
      }
      "not get a value that was never inserted" in {
        val first :: second :: Nil = createKadActNetwork(0, 15)
        val key: Key = 1

        first.node ! GetFromNetwork(key)
        expectMsg(None)

        second.node ! GetFromNetwork(key)
        expectMsg(None)
      }
      "handle the FindNode protocol message" in {
        val first :: second :: _ = createKadActNetwork(0, 15)
        first.node ! kadact.messages.FindNode(second, 0, 0) // Look for key 0 in node 0
        expectMsg(kadact.messages.FindNodeResponse(first, 0, Set(first)))
        //^- "The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor."
      }
      "handle the FindValue protocol message" in {
        val first :: second :: _ = createKadActNetwork(0, 15)
        first.node ! kadact.messages.FindValue(second, 0, 0) // Look for key 0 in node 0
        expectMsg(kadact.messages.FindValueResponse(first, 0, Right(Set(first))))
        // ^-- No value was set in the node yet, so it should return a list of nodes
      }
      "handle the Ping protocol message" in {
        val first :: second :: _ = createKadActNetwork(0, 15)
        first.node ! kadact.messages.Ping(second, 0)
        expectMsg(kadact.messages.Pong(first, 0))
      }
      "handle the Store protocol message" in {
        val first :: second :: _ = createKadActNetwork(0, 15)
        first.node ! kadact.messages.Store(second, generation = 0, 0, 0)
        expectMsg(kadact.messages.StoreResponse(first, generation = 0))
      }
      "get previously stored values when requested to the same node" in {
        val first :: _ = createKadActNetwork(0, 15)
        val key: Key = 1

        first.node ! AddToNetwork(key, 10)
        expectMsg(Done)

        first.node ! GetFromNetwork(key)
        expectMsg(Some(10))
      }
      "get previously stored values when requested to another node (1)" in {
        val first :: second :: Nil = createKadActNetwork(0, 15)
        val key: Key = 1

        first.node ! AddToNetwork(key, 10)
        expectMsg(Done)

        second.node ! GetFromNetwork(key)
        expectMsg(Some(10))
      }
      "get previously stored values when requested to another node (2)" in {
        val first :: second :: Nil = createKadActNetwork(0, 15)
        val key: Key = 1

        second.node ! AddToNetwork(key, 10)
        expectMsg(Done)

        first.node ! GetFromNetwork(key)
        expectMsg(Some(10))
      }
      "be able to store the whole range of keys" in {
        val first :: second :: _ = createKadActNetwork(0, 15)

        for(key <- 0 to 15) {
          first.node ! AddToNetwork(key, key*100)
          expectMsg(Done)
        }
        for(key <- 0 to 15) {
          first.node ! GetFromNetwork(key)
          expectMsg(Some(key*100))
          second.node ! GetFromNetwork(key)
          expectMsg(Some(key*100))
        }
      }
    }

    "when the network is full of nodes" must {
      "be able to create such a network" ignore {
        val nodes = createKadActNetwork(0 to 15 : _*)
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

  def getContact(node: ActorRef): Contact = {
    import akka.pattern.ask
    Await.result((node ? GetContact).mapTo[Contact], timeout.duration)
  }

  def createKadActNetwork(ids: Int*) : List[Contact] = {
    val original = start(newKadActNode(ids.head))
    val originalContact = getContact(original)

    val contacts =
      ids.tail.foldLeft(List(originalContact)) {
        (contactsSoFar, id) =>
          val joining = newKadActNode(id) // the farthest away
          joining ! Join(originalContact)
          expectMsg(Done)
          getContact(joining) :: contactsSoFar
      }
    contacts.reverse
  }

}
