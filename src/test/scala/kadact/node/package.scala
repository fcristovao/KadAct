package kadact

import kadact.node._
import akka.testkit.TestProbe
import akka.actor.ActorSystem

package object nodeForTest {
  implicit def intToKey(int: Int): Key = BigInt(int)

  def mockContacts(nodeIds : Int*)(implicit system: ActorSystem) = {
    val probesAndContacts =
      for (nodeId <- nodeIds;
           testProbe = TestProbe())
      yield (testProbe, Contact(nodeId, testProbe.ref))
    probesAndContacts.unzip
  }
}
