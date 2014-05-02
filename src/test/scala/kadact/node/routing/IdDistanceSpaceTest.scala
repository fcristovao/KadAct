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

  val (_, allContacts) = mockContacts(0 to 15: _*)
  val origin = 0

  "An LeafIdSpace" should {
    "be instantiable" in {
      LeafIdSpace(origin)
    }
    "never insert the contact that refers to its origin" in {
      val space = LeafIdSpace(origin)
      an[IllegalArgumentException] should be thrownBy space.insert(allContacts(origin))
    }
    "not split even if more than config.k contacts are inserted in the upper half of the ID space" in {
      val space = LeafIdSpace(origin)
      space.insert(allContacts(13)) shouldBe LeafIdSpace(origin = 0, depth = 0, startDistance = 0)
      space.insert(allContacts(14)) shouldBe LeafIdSpace(origin = 0, depth = 0, startDistance = 0)
      // No splitting should happen:
      space.insert(allContacts(15)) shouldBe LeafIdSpace(origin = 0, depth = 0, startDistance = 0)
    }
  }


  "An IdDistanceSpace" should {
    "be instantiable" in {
      IdDistanceSpace(0)
    }
    "never insert the contact that refers to its origin" in {
      val origin = 0
      val space = IdDistanceSpace(origin)
      val (_, contacts) = mockContacts(origin)
      an[IllegalArgumentException] should be thrownBy space.insert(contacts(0))
    }
    "be able to insert a new contact" in {
      val space = IdDistanceSpace(0)
      val (_, contacts) = mockContacts(1)
      space.insert(contacts(0))
    }
    "return an empty set when no insert was ever done" in {
      val space = IdDistanceSpace(0)
      space.pickNNodesCloseTo(config.k, 1) shouldBe empty
    }
    "return the contacts it knows about" in {
      val space = IdDistanceSpace(0)
      val (_, contacts) = mockContacts(14, 15) // 4 bits space, these are the farthest away

      for (contact <- contacts) {
        space.insert(contact)
      }
      space.pickNNodesCloseTo(config.k, 14) shouldBe contacts.toSet
    }
    "replace an older contact in the same id range if a newer is inserted" ignore {
      var space = IdDistanceSpace(0)
      space = space.insert(TimestampedContact(0, allContacts(14)))
      space shouldBe LeafIdSpace(origin = 0, depth = 0, startDistance = 0)
      space = space.insert(TimestampedContact(1, allContacts(13)))
      space shouldBe LeafIdSpace(origin = 0, depth = 0, startDistance = 0)
      space = space.insert(TimestampedContact(2, allContacts(15)))
      // No splitting should happen, just a replacement
      space shouldBe LeafIdSpace(origin = 0, depth = 0, startDistance = 0)
    }
    "return the most recent contacts it knows about" ignore {
      val space = IdDistanceSpace(0)
      val (_, contacts) = mockContacts(14, 13, 15) // 4 bits space, these are the farthest away

      for ((contact, i) <- contacts.zipWithIndex) {
        space.insert(TimestampedContact(i * 1000, contact))
      }
      space.pickNNodesCloseTo(config.k, 14) shouldBe contacts.takeRight(2).toSet
    }
  }

}
