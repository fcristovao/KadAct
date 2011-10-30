package kadact.node

import scala.collection.mutable.PriorityQueue

import kadact.KadAct

class KBucket{
	class TimestampedContact(val timestamp: Long, val contact: Contact) extends Ordered[TimestampedContact] {
		override def compare(that: TimestampedContact): Int = {
			val tmp = this.timestamp.compare(that.timestamp) 
			if(tmp == 0){
				this.contact.nodeID.compare(that.contact.nodeID)
			} else {
				tmp
			}
		}
	}
	
	object LeastRecentlySeenOrdering extends Ordering[TimestampedContact] {
		override def compare(x: TimestampedContact, y: TimestampedContact) = {
			x.compareTo(y)
		}
	}
	
	val k = KadAct.k
	
	val queue = new PriorityQueue[TimestampedContact]()(LeastRecentlySeenOrdering)
	
	def isFull : Boolean = {
		true
	}
	
	def insert(contact: Contact) : Boolean = {
		true
	}
	
	def tryToInsert(contact: Contact) : Boolean = {
		true
	}
	
	def filter(f: (Contact) => Boolean) : Iterable[Contact] = {
		queue.filter(tc => f(tc.contact)).map(_.contact)
	}
	
}