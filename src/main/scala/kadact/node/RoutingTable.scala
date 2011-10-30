package kadact.node

import kadact.KadAct

class RoutingTable(nodeID: NodeID) {
	
	var rootIDSpace = new LeafIDSpace(nodeID)
	
	abstract class IDDistanceSpace(val nodeID: NodeID, val depth: Int, val startDistance: Distance) {
		lazy val range = BigInt(2).pow(KadAct.B-depth)
		
		def insert(contact: Contact): (IDDistanceSpace, Boolean) = {
			insert(contact, distance(nodeID, contact.nodeID))
		}
		
		def insert(contact: Contact, distance: Distance) : (IDDistanceSpace, Boolean)
		
		def contains(distance: Distance) = {
			distance >= startDistance && distance < range
		}
		
	}
	
	case class SplittedIDSpace(var lower: IDDistanceSpace, var greater: IDDistanceSpace) extends IDDistanceSpace(lower.nodeID, lower.depth+1, lower.startDistance) {
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
		
	}
	
	case class LeafIDSpace(override val nodeID: NodeID, override val depth: Int = 0, override val startDistance: Distance = 0) extends IDDistanceSpace(nodeID, depth, startDistance) {
		val bucket : KBucket = new KBucket()
		
		protected def isSplittable: Boolean = {
			startDistance == 0
		}
		
		def insert(contact: Contact, distance: Distance) : (IDDistanceSpace, Boolean) = {
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
					
			val lower = new LeafIDSpace(nodeID, depth + 1, startDistance)
			val greater = new LeafIDSpace(nodeID, depth + 1, startDistance + range)
			
			bucket.
				filter(contact => lower.contains(node.distance(nodeID, contact.nodeID))).
				foreach(lower.insert(_))
				
			bucket.
				filter(contact => greater.contains(node.distance(nodeID, contact.nodeID))).
				foreach(greater.insert(_))
				
			SplittedIDSpace(lower, greater)
		}
	}

	
	
	
	
}