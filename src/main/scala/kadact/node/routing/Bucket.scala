package kadact.node.routing

import kadact.KadAct
import scala.collection.immutable.TreeSet

import kadact.node.Contact

abstract class Bucket(val maxSize: Int){
	def queue: TreeSet[TimestampedContact]
	def queue_=(set: TreeSet[TimestampedContact]): Unit
	
	def isFull : Boolean = {
		queue.size >= maxSize
	}
	
	def insertOrUpdate(contact: Contact) : (Boolean, Option[TimestampedContact]) = {
		insertOrUpdate(new TimestampedContact(System.currentTimeMillis(), contact))
	}
	
	def insertOrUpdate(tsContact: TimestampedContact) : (Boolean, Option[TimestampedContact])
	
	def filter(f: (Contact) => Boolean) : Set[Contact] = {
		queue.filter(tc => f(tc.contact)).map(_.contact)
	}
	
	def pickNNodes(n: Int): Set[Contact] = {
		queue.take(n).map(_.contact).toSet[Contact]
	}
}