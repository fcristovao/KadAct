package kadact.node.routing.buckets

import scala.collection.immutable.TreeSet
import kadact.config.KadActConfig
import kadact.node.routing.{LeastRecentlySeenOrdering, TimestampedContact}

class KBucket(implicit config: KadActConfig) extends Bucket(config.k) {
  override var queue = new TreeSet[TimestampedContact]()(LeastRecentlySeenOrdering)

  //var replacementCache

  override def insertOrUpdate(tsContact: TimestampedContact): (Boolean, Option[TimestampedContact]) = {
    queue.find(_.contact == tsContact.contact) match {
      case Some(timestampedContact) => {
        //there's already one entry in the queue for this contact. We should update the entry.
        queue -= timestampedContact
        queue += tsContact
        (true, None)
      }
      case None => {
        if (!isFull) {
          queue += tsContact
          (true, None)
        } else {
          (false, None)
        }
      }
    }
  }

}