package kadact.node

import akka.actor.Actor
import kadact.KadAct

object RoutingTable {
	sealed trait Messages
	case class Insert(contact: Contact) extends Messages
	case class PickNNodesCloseTo(n: Int, nodeID: NodeID) extends Messages
	
}

class RoutingTable(origin: Contact) extends Actor {
	import RoutingTable._
	
	var rootIDSpace : IDDistanceSpace = new LeafIDSpace(origin)
	
	abstract class IDDistanceSpace(val origin: Contact, val depth: Int, val startDistance: Distance) {
		val range = BigInt(2).pow(KadAct.B - depth)
		
		def insert(contact: Contact): (IDDistanceSpace, Boolean) = {
			insert(contact, distance(origin.nodeID, contact.nodeID))
		}
		
		def insert(contact: Contact, distance: Distance) : (IDDistanceSpace, Boolean)
		
		def contains(distance: Distance) = {
			distance >= startDistance && distance < range
		}
		
		def pickAlphaNodesCloseTo(distance: Distance): Set[Contact] 
		
	}
	
	case class SplittedIDSpace(var lower: IDDistanceSpace, var greater: IDDistanceSpace) extends IDDistanceSpace(lower.origin, lower.depth+1, lower.startDistance) {
		def insert(contact: Contact, distance: Distance) : (IDDistanceSpace, Boolean) = {
			assert(lower.contains(distance) || greater.contains(distance))
			
			val result =
				if(lower.contains(distance)){
					val (newLower, result) = lower.insert(contact, distance)
					lower = newLower
					result
				} else {
					val (newGreater, result) = greater.insert(contact, distance)
					greater = newGreater
					result
				}
			(this, result)
		}
		
		protected def spaceClosestTo(distance: Distance): IDDistanceSpace = {
			if(lower.contains(distance)){
				lower
			} else if (greater.contains(distance)) {
				greater
			} else {
				throw new Exception("this should never happen")
			}
		}
		
		protected def spaceFarthestTo(distance: Distance): IDDistanceSpace = {
			if(lower.contains(distance)){
				greater
			} else if (greater.contains(distance)) {
				lower
			} else {
				throw new Exception("this should never happen")
			}
		}
		
		protected def otherHalf(halfSpace: IDDistanceSpace) : IDDistanceSpace = {
			if(halfSpace == lower) {
				greater
			} else if(halfSpace == greater) {
				lower
			} else {
				throw new Exception("this should never happen")
			}
		}
		
		def pickAlphaNodesCloseTo(distance: Distance): Set[Contact] = {
			val space = spaceClosestTo(distance)
			val result = space.pickAlphaNodesCloseTo(distance)

			if(result.size < KadAct.alpha){
				(result union otherHalf(space).pickAlphaNodesCloseTo(distance)).take(KadAct.alpha)
			} else {
				result
			}
		}
		
	}
	
	case class LeafIDSpace(override val origin: Contact, override val depth: Int = 0, override val startDistance: Distance = 0) extends IDDistanceSpace(origin, depth, startDistance) {
		val bucket : KBucket = new KBucket()
		
		protected def isSplittable: Boolean = {
			startDistance == 0
		}
		
		def insert(contact: Contact, distance: Distance) : (IDDistanceSpace, Boolean) = {
			assert(this.contains(distance))
			val inserted = bucket.tryToInsert(contact)
			
			if(!inserted && isSplittable){
				val newIDSpace = split()
				newIDSpace.insert(contact, distance)
			} else {
				(this, false)
			}
		}
		
		def split(): SplittedIDSpace = {
			import kadact.node
					
			val lower = new LeafIDSpace(origin, depth + 1, startDistance)
			val greater = new LeafIDSpace(origin, depth + 1, startDistance + range)
			
			bucket.
				filter(contact => lower.contains(node.distance(origin.nodeID, contact.nodeID))).
				foreach(lower.insert(_))
				
			bucket.
				filter(contact => greater.contains(node.distance(origin.nodeID, contact.nodeID))).
				foreach(greater.insert(_))
				
			SplittedIDSpace(lower, greater)
		}
		
		def pickAlphaNodesCloseTo(distance: Distance): Set[Contact] = {
			assert(this.contains(distance))
			bucket.pickAlphaNodes()
		}
	}

	def receive = {
		case Insert(contact) => {
			val (newRoot, result) = this.rootIDSpace.insert(contact)
			this.rootIDSpace = newRoot
			result
		}
		case PickNNodesCloseTo(n, nodeID) => {
			self.reply(rootIDSpace.pickAlphaNodesCloseTo(distance(origin.nodeID, nodeID)))
		}
	}
	
	
}