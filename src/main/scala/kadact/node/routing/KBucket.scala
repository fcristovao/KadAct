package kadact.node.routing

import scala.collection.immutable.TreeSet

import kadact.KadAct
import kadact.node.Contact

class KBucket extends Bucket(KadAct.k){
	override var queue = new TreeSet[TimestampedContact]()(LeastRecentlySeenOrdering)
	
	override def insertOrUpdate(tsContact: TimestampedContact) : (Boolean, Option[TimestampedContact]) = {
		queue.find(_.contact == tsContact.contact) match {
			case Some(timestampedContact) => { //there's already one entry in the queue for this contact. We should update the entry.
				queue -= timestampedContact
				queue += tsContact
				(true, None)
			}
			case None => {
				if(!isFull){
					queue += tsContact
					(true, None)
				} else {
					(false, None)
				}
			}
		}
	}
	
}