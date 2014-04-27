package kadact.node.routing

import org.scalatest.{BeforeAndAfterAll, WordSpecLike, Matchers}
import kadact.config.TestKadActConfig
import kadact.nodeForTest._
import akka.testkit.TestKit
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

class IdDistanceSpaceTest extends TestKit(ActorSystem("test", ConfigFactory.load("application-test")))
                                  with WordSpecLike with Matchers with BeforeAndAfterAll with TestKadActConfig {

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "An IdDistanceSpace" should {
    "be instantiable" in {
      IdDistanceSpace(0)
    }
    "never insert the contact that refers to its origin" in {
      val origin = 0
      val space = IdDistanceSpace(origin)
      val (_, contacts) = mockContacts(origin)
      an [IllegalArgumentException] should be thrownBy space.insert(contacts(0))
    }
    "be able to insert a new contact" in {
      val space = IdDistanceSpace(0)
      val (_, contacts) = mockContacts(1)
      space.insert(contacts(0))
    }
    "return an empty set when no insert was ever done" in {
      val space = IdDistanceSpace(0)
      space.pickNNodesCloseTo(config.k, 1) shouldBe Set()
    }
  }

}
