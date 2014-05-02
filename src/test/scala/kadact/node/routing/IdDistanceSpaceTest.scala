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
    "never insert outside its range" in {
      val space = LeafIdSpace(origin, depth = 1, startDistance = 0) // from 0 to 7
      an[IllegalArgumentException] should be thrownBy space.insert(allContacts(8))
    }
    "split if more than config.k contacts are inserted in the ID space (1)" in {
      val space = LeafIdSpace(origin)
      space.insert(allContacts(13)) shouldBe LeafIdSpace(origin = 0, depth = 0, startDistance = 0)
      space.insert(allContacts(14)) shouldBe LeafIdSpace(origin = 0, depth = 0, startDistance = 0)
      // Splitting should happen:
      space.insert(allContacts(15)) shouldBe SplittedIdSpace(
        LeafIdSpace(origin = 0, depth = 1, startDistance = 0),
        LeafIdSpace(origin = 0, depth = 1, startDistance = 8)
      )
    }
    "split if more than config.k contacts are inserted in the ID space (2)" in {
      // TODO: should this be really the case? This is a mere optimization, but...
      val space = LeafIdSpace(origin)
      space.insert(allContacts(5)) shouldBe LeafIdSpace(origin = 0, depth = 0, startDistance = 0)
      space.insert(allContacts(6)) shouldBe LeafIdSpace(origin = 0, depth = 0, startDistance = 0)
      // Splitting should happen:
      space.insert(allContacts(7)) shouldBe SplittedIdSpace(
        SplittedIdSpace(LeafIdSpace(0, 2, 0), LeafIdSpace(0, 2, 4)),
        LeafIdSpace(origin = 0, depth = 1, startDistance = 8)
      )
    }
    "split if more than config.k contacts are inserted in the ID space (3)" in {
      val initialDepth = 2
      val space = LeafIdSpace(origin, initialDepth, 0) // 0 to 3 range
      space.insert(allContacts(1)) shouldBe LeafIdSpace(origin, initialDepth, startDistance = 0)
      space.insert(allContacts(2)) shouldBe LeafIdSpace(origin, initialDepth, startDistance = 0)
      // Splitting should happen:
      space.insert(allContacts(3)) shouldBe SplittedIdSpace(
        LeafIdSpace(origin, initialDepth + 1, startDistance = 0),
        LeafIdSpace(origin, initialDepth + 1, startDistance = 2)
      )
    }
  }


  "An IdDistanceSpace" should {
    "be instantiable" in {
      IdDistanceSpace(origin)
    }
    "never insert the contact that refers to its origin" in {
      val space = IdDistanceSpace(origin)
      an[IllegalArgumentException] should be thrownBy space.insert(allContacts(origin))
    }
    "be able to insert a new contact" in {
      val space = IdDistanceSpace(0)
      space.insert(allContacts(1))
    }
    "return an empty set when no insert was ever done" in {
      val space = IdDistanceSpace(origin)
      space.pickNNodesCloseTo(config.k, 1) shouldBe empty
    }
    "be fully split when all the contacts were inserted" in {
      var space = IdDistanceSpace(origin)
      for (contact <- allContacts.tail) {
        space = space.insert(contact)
      }
      space shouldBe SplittedIdSpace(
        SplittedIdSpace(
          SplittedIdSpace(LeafIdSpace(0, 3, 0), LeafIdSpace(0, 3, 2)),
          LeafIdSpace(0, 2, 4)
        ),
        LeafIdSpace(0, 1, 8)
      )
    }
    "have the appropriate amount of contacts when full" in {
      var space = IdDistanceSpace(origin)
      for (contact <- allContacts.tail) {
        space = space.insert(contact)
      }
      // k = 2, per test config
      // [0, 2[ - k-1 contacts (origin doesn't count)
      // [2, 4[ - k contacts
      // [4, 8[ - k contacts
      // [8,16[ - k contacts
      space.pickNNodesCloseTo(allContacts.size, origin) should have size 7
    }
    "return the contacts it knows about" in {
      val space = IdDistanceSpace(origin)

      for (contactId <- 14 to 15) {
        space.insert(allContacts(contactId))
      }
      space.pickNNodesCloseTo(config.k, 14) should have size 2
    }
  }

}
