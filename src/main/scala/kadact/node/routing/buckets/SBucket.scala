package kadact.node.routing.buckets

import scala.collection.immutable.TreeSet
import kadact.node._
import kadact.config.KadActConfig
import kadact.node.routing.{ClosestToOrdering, LeastRecentlySeenOrdering, TimestampedContact}

class SBucket(origin: NodeID)(implicit config: KadActConfig) extends Bucket(5 * config.s) {
  override var queue = new TreeSet[TimestampedContact]()(LeastRecentlySeenOrdering)

  val comparator = ClosestToOrdering(origin)
  /* This list contains exactly the same contacts than the "queue", however, sorted by how close they are to the origin node
   * This way one can guarantee that this bucket will only contain the closest nodes to origin, while still providing with the
   * nodes that are least-recently-seen when asked.
   */
  var closestOrderedNodes = new TreeSet[TimestampedContact]()(comparator)

  private def remove(tsContact: TimestampedContact) = {
    closestOrderedNodes -= tsContact
    queue -= tsContact
  }

  private def add(tsContact: TimestampedContact) = {
    closestOrderedNodes += tsContact
    queue += tsContact
  }

  override def insertOrUpdate(tsContact: TimestampedContact): (Boolean, Option[TimestampedContact]) = {
    if (!isFull) {
      add(tsContact)
      (true, None)
    } else {
      val farthest = closestOrderedNodes.last
      if (comparator.lt(tsContact, farthest)) {
        /* It means that the farthest we know so far (closestOrderedNodes.last) it's farther than the one we're trying now,
         * so it must be discarded, and the new one inserted.
         */
        remove(farthest)
        add(tsContact)
        (true, Some(farthest))
      } else {
        /* In this case, either the contact is equal to one existing (and we only update the timestamp),
         * or it won't be inserted in this bucket at all.
         */
        queue.find(_.contact == tsContact.contact) match {
          case Some(timestampedContact) => {
            /* There's already one entry in the queue for this contact.
             * Update the entry, but only if the timestamp is newer.
             */
            if (timestampedContact isOlderThan tsContact) {
              remove(timestampedContact)
              add(tsContact)
            }
            (true, None)
          }
          case None => {
            /* This contact doesn't exist in this bucket but the one trying to insert is farther than those existing,
             * so we don't insert it.
             */
            (false, None)
          }
        }
      }
    }
  }
}