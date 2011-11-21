package kadact.node.routing

import akka.actor.Actor
import akka.actor.Actor._
import akka.event.EventHandler

import kadact.KadAct
import kadact.node.Contact
import kadact.node._


object RoutingTable {
	sealed trait Messages
	case class Insert(contact: Contact) extends Messages
	case class PickNNodesCloseTo(n: Int, nodeID: NodeID) extends Messages
	case object SelectRandomIDs extends Messages
	
}

class RoutingTable(origin: NodeID) extends Actor {
	import RoutingTable._
	
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
			val result = siblings.pickNNodes(n)

			val tmp = if(result.size < n){
				(result union rootIDSpace.pickNNodesCloseTo(n - result.size, distance(this.origin, nodeID)))
			} else {
				result
			}
			EventHandler.debug(self, "Picked N nodes: "+tmp)
			self.reply(tmp)
		}
		case SelectRandomIDs => {
			val tmp = rootIDSpace.selectRandomNodeIDs()
			self.reply(tmp)
		}
	}
	
	
}