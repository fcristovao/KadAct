package kadact.node

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import kadact.config.TestKadActConfig
import com.typesafe.config.ConfigFactory
import akka.actor.{Props, ActorSystem}
import kadact.node.KadActNode._
import kadact.config.modules.{LookupManagerModule, RoutingTableModule}
import kadact.node.KadActNode.AddToNetwork


class KadActNodeTest extends TestKit(ActorSystem("test", ConfigFactory.load("application-test")))
                             with WordSpecLike with MustMatchers with ImplicitSender with BeforeAndAfterAll {

  implicit val config = TestKadActConfig()
  implicit val injector = new RoutingTableModule :: new LookupManagerModule

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "An KadActNode actor" when {
    "alone in the network" must {
      "be able to start" in {
        startKadActNode
      }
      "return the Contact with the correct nodeId" in {
        val nodeFSM = startKadActNode
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
        val nodeFSM = startKadActNode
        val key: Key = BigInt(1)

        nodeFSM ! AddToNetwork(key, 10)
        expectMsg(Done)
      }
      "get previously stored values" ignore {
        val nodeFSM = startKadActNode
        val key: Key = BigInt(1)

        nodeFSM ! AddToNetwork(key, 10)
        expectMsg(Done)

        nodeFSM ! GetFromNetwork(key)
        expectMsg(key -> 10)
      }
    }
  }

  def startKadActNode = {
    val nodeFSM = system.actorOf(Props(new KadActNode[Int](BigInt(0))))
    nodeFSM ! Start
    expectMsg(Done)
    nodeFSM
  }
}
