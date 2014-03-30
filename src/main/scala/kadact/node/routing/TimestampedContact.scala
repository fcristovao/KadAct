package kadact.node.routing

import kadact.node._

private[routing] class TimestampedContact(val timestamp: Long, val contact: Contact) extends Ordered[TimestampedContact] {
  override def compare(that: TimestampedContact): Int = LeastRecentlySeenOrdering.compare(this, that)

  def isOlderThan(that: TimestampedContact): Boolean = timestamp.compare(that.timestamp) < 0

  def isNewerThan(that: TimestampedContact): Boolean = timestamp.compare(that.timestamp) > 0
}

private[routing] object LeastRecentlySeenOrdering extends Ordering[TimestampedContact] {
  override def compare(x: TimestampedContact, y: TimestampedContact) = {
    val tmp = x.timestamp.compare(y.timestamp)
    if (tmp == 0) {
      x.contact.nodeID.compare(y.contact.nodeID)
    } else {
      tmp
    }
  }
}

private[routing] case class ClosestToOrdering(origin: NodeID) extends Ordering[TimestampedContact] {
  override def compare(x: TimestampedContact, y: TimestampedContact) = {
    val distanceX = distance(x.contact.nodeID, origin)
    val distanceY = distance(y.contact.nodeID, origin)

    if (distanceX == distanceY) {
      0
    } else if (distanceX < distanceY) {
      -1
    } else {
      1
    }
  }
}

