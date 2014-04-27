package kadact.node.routing

import akka.actor.{IndirectActorProducer, Actor, ActorLogging}
import akka.event.LoggingReceive
import kadact.node._
import scala.collection.immutable.TreeSet
import kadact.config.KadActConfig
import scaldi.{Injector, Injectable}


object RoutingTable {
  sealed trait Messages
  case class Insert(contact: Contact) extends Messages
  case class PickNNodesCloseTo(n: Int, nodeID: NodeID) extends Messages
  case object SelectRandomIDs extends Messages
  case class Failed(contact: Contact) extends Messages
}

trait RoutingTableFactory {
  def build(origin: NodeID)(implicit config: KadActConfig): RoutingTable
}

class RoutingTableProducer(origin: NodeID)(implicit config: KadActConfig, injector: Injector)
  extends IndirectActorProducer with Injectable {
  override def actorClass = classOf[RoutingTable]

  override def produce = inject[RoutingTableFactory].build(origin)
}

class RoutingTable(origin: NodeID)(implicit config: KadActConfig) extends Actor with ActorLogging {
  import RoutingTable._

  var rootIDSpace: IdDistanceSpace = IdDistanceSpace(origin)
  val siblings: SBucket = new SBucket(origin)

  def receive = LoggingReceive {
    case Insert(contact) => {
      //"[KademliaSpec] A node should never put its own NodeID into a bucket as a contact"
      if (contact.nodeID != origin) {
        // Try to insert the contact in our siblings list
        siblings.insertOrUpdate(contact) match {
          // The contact was inserted and no other contact had to be removed from the siblings list
          case (true, None) => {
            // nothing do do
          }
          // The contact wasn't inserted (it is too far away), so it must inserted in the other KBuckets
          case (false, None) => {
            val (newRoot, result) = this.rootIDSpace.insert(contact)
            this.rootIDSpace = newRoot
          }
          // The contact was inserted and one contact that already existed now must be inserted in the KBuckets
          case (true, Some(removedContact)) => {
            val (newRoot, result) = this.rootIDSpace.insert(removedContact)
            this.rootIDSpace = newRoot
          }
        }
      }
    }
    case PickNNodesCloseTo(n, nodeID) => {
      /* This is highly inefficient but for now it works as expected */
      var result = new TreeSet[Contact]()(kadact.node.ContactClosestToOrdering(nodeID))
      result ++= siblings.pickNNodes(n)
      result ++= rootIDSpace.pickNNodesCloseTo(n, nodeID)

      val tmp = result.take(n)
      log.debug("Picked " + n + " nodes: " + tmp)
      sender ! tmp
    }
    case SelectRandomIDs => {
      val tmp = rootIDSpace.selectRandomNodeIDs()
      sender ! tmp
    }
  }


}