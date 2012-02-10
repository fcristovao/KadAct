package kadact.node.routing

import akka.actor.Actor
import akka.actor.Actor._
import akka.event.EventHandler
import kadact.KadAct
import kadact.node.Contact
import kadact.node._
import scala.collection.immutable.TreeSet


object RoutingTable {
	sealed trait Messages
	case class Insert(contact: Contact) extends Messages
	case class PickNNodesCloseTo(n: Int, nodeID: NodeID) extends Messages
	case object SelectRandomIDs extends Messages
	
}

class RoutingTable(originalNode: Contact, origin: NodeID) extends Actor {
	import RoutingTable._
	
	def this(originalNode: Contact) = this(originalNode, originalNode.nodeID)
	
	var rootIDSpace: IDDistanceSpace = new LeafIDSpace(origin)
	val siblings: SBucket = new SBucket(origin)
	
	def receive = loggable(self){
		case Insert(contact) => {
			if(contact.nodeID != origin){ //"A node should never put its own NodeID into a bucket as a contact"
				siblings.insertOrUpdate(contact) match {
					case (_, Some(removedContact)) => {
						val (newRoot, result) = this.rootIDSpace.insert(removedContact)
						this.rootIDSpace = newRoot
					}
					case (false, None) => {
						val (newRoot, result) = this.rootIDSpace.insert(contact)
						this.rootIDSpace = newRoot
					}
				}
			}
		}
		case PickNNodesCloseTo(n, nodeID) => {
			/* This is highly inefficient but for now it works as expected */
			var result = new TreeSet[Contact]()(kadact.node.ContactClosestToOrdering(nodeID))
			result += originalNode
			result ++= siblings.pickNNodes(n)
			result ++= rootIDSpace.pickNNodesCloseTo(n, distance(this.origin, nodeID))

			val tmp = result.take(n)
			EventHandler.debug(self, "Picked N nodes: "+tmp)
			self.reply(tmp)
		}
		case SelectRandomIDs => {
			val tmp = rootIDSpace.selectRandomNodeIDs()
			self.reply(tmp)
		}
	}
	
	
}