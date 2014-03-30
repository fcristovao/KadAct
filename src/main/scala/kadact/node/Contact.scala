package kadact.node

import akka.actor.ActorRef

case class Contact(var nodeID: NodeID, var node: ActorRef) {
  override def toString(): String = {
    "Contact(" + nodeID + ")"
  }

  override def equals(that: Any): Boolean = {
    that match {
      case Contact(nodeID, _) => this.nodeID.equals(nodeID)
      case _ => false
    }
  }

  override def hashCode(): Int = {
    nodeID.hashCode()
  }
}

private[node] case class ContactClosestToOrdering(origin: NodeID) extends Ordering[Contact] {
  override def compare(x: Contact, y: Contact) = {
    val distanceX = distance(x.nodeID, origin)
    val distanceY = distance(y.nodeID, origin)

    if (distanceX == distanceY) {
      0
    } else if (distanceX < distanceY) {
      -1
    } else {
      1
    }
  }
}