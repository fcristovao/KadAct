package kadact.node.routing

import scala.util.Random

import kadact.KadAct
import kadact.node.Contact
import kadact.node._

abstract class IDDistanceSpace(val origin: NodeID, val depth: Int, val startDistance: Distance) {
	val range = BigInt(2).pow(KadAct.B - depth)
	val rand = new Random()
	
	def insert(contact: Contact): (IDDistanceSpace, Boolean) = {
		insert(contact, distance(origin, contact.nodeID))
	}
	
	def insert(tsContact: TimestampedContact): (IDDistanceSpace, Boolean) = {
		insert(tsContact, distance(origin, tsContact.contact.nodeID))
	}
	
	def insert(contact: Contact, distance: Distance) : (IDDistanceSpace, Boolean) = {
		insert(new TimestampedContact(System.currentTimeMillis(), contact), distance)
	}
	
	def insert(tsContact: TimestampedContact, distance: Distance) : (IDDistanceSpace, Boolean)
	
	def contains(distance: Distance) = {
		distance >= startDistance && distance < (startDistance + range)
	}
	
	def pickNNodesCloseTo(n: Int, distance: Distance): Set[Contact]
	
	def selectRandomNodeIDs() : Set[NodeID]
	
	def randomNodeID() = {
		BigInt(KadAct.B-depth, rand)
	}
	
}

case class SplittedIDSpace(var lower: IDDistanceSpace, var greater: IDDistanceSpace) extends IDDistanceSpace(lower.origin, lower.depth - 1, lower.startDistance) {
	override def insert(tsContact: TimestampedContact, distance: Distance) : (IDDistanceSpace, Boolean) = {
		assert(lower.contains(distance) || greater.contains(distance))
		
		val result =
			if(lower.contains(distance)){
				val (newLower, result) = lower.insert(tsContact, distance)
				lower = newLower
				result
			} else {
				val (newGreater, result) = greater.insert(tsContact, distance)
				greater = newGreater
				result
			}
		(this, result)
	}
	
	override def selectRandomNodeIDs() : Set[NodeID] = {
		lower.selectRandomNodeIDs() union greater.selectRandomNodeIDs()
	}
	
	protected def spaceClosestTo(distance: Distance): IDDistanceSpace = {
		if(lower.contains(distance) || distance < startDistance){
			lower
		} else if (greater.contains(distance) || distance >= startDistance + range) {
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
	
	override def pickNNodesCloseTo(n: Int, distance: Distance): Set[Contact] = {
		val space = spaceClosestTo(distance)
		val result = space.pickNNodesCloseTo(n, distance)

		if(result.size < n){
			(result union otherHalf(space).pickNNodesCloseTo(n - result.size, distance))
		} else {
			result
		}
	}
	
}

case class LeafIDSpace(override val origin: NodeID, override val depth: Int = 0, override val startDistance: Distance = 0) extends IDDistanceSpace(origin, depth, startDistance) {
	val bucket: KBucket = new KBucket()
	
	protected def isSplittable: Boolean = {
		startDistance == 0
	}
	
	def insert(tsContact: TimestampedContact, distance: Distance) : (IDDistanceSpace, Boolean) = {
		assert(this.contains(distance))
		val (inserted, _) = bucket.insertOrUpdate(tsContact)
		
		if(!inserted && isSplittable){
			val newIDSpace = split()
			newIDSpace.insert(tsContact, distance)
		} else {
			(this, false)
		}
	}
	
	def split(): SplittedIDSpace = {
		import kadact.node
				
		val lower = new LeafIDSpace(origin, depth + 1, startDistance)
		val greater = new LeafIDSpace(origin, depth + 1, startDistance + (range/2))
		
		bucket.
			filter(contact => lower.contains(node.distance(origin, contact.nodeID))).
			foreach(lower.insert(_))
			
		bucket.
			filter(contact => greater.contains(node.distance(origin, contact.nodeID))).
			foreach(greater.insert(_))
			
		SplittedIDSpace(lower, greater)
	}
	
	override def pickNNodesCloseTo(n: Int, distance: Distance): Set[Contact] = {
		assert(this.contains(distance)) // this assertion may not hold, because we might have to look for Nodes when the nearest bucket did not have the required k entries
		bucket.pickNNodes(n)
	}
	
	override def selectRandomNodeIDs() : Set[NodeID] = {
		Set(randomNodeID())
	}
}