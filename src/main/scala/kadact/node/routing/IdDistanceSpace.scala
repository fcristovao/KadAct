package kadact.node.routing

import scala.util.Random
import kadact.node._
import kadact.config.KadActConfig
import kadact.node.Contact

abstract class IdDistanceSpace(val origin: NodeID, val depth: Int, val startDistance: Distance)(implicit config: KadActConfig) {
  val range = BigInt(2).pow(config.B - depth)
  val rand = new Random()

  def insert(contact: Contact): (IdDistanceSpace, Boolean) = {
    insert(new TimestampedContact(System.currentTimeMillis(), contact))
  }

  def insert(tsContact: TimestampedContact): (IdDistanceSpace, Boolean)

  def contains(nodeId: NodeID): Boolean = {
    val distance = kadact.node.distance(origin, nodeId)
    distance >= startDistance && distance < (startDistance + range)
  }

  def pickNNodesCloseTo(n: Int, nodeId: NodeID): Set[Contact]

  def selectRandomNodeIDs(): Set[NodeID]

  def randomNodeID() = {
    BigInt(config.B - depth, rand)
  }
}

object IdDistanceSpace {
  def apply(origin: NodeID)(implicit config: KadActConfig): IdDistanceSpace = {
    LeafIdSpace(origin)
  }
}

case class SplittedIDSpace(var lower: IdDistanceSpace, var greater: IdDistanceSpace)(implicit config: KadActConfig)
  extends IdDistanceSpace(lower.origin, lower.depth - 1, lower.startDistance) {
  override def insert(tsContact: TimestampedContact): (IdDistanceSpace, Boolean) = {
    val nodeId = tsContact.contact.nodeID
    assert(lower.contains(nodeId) || greater.contains(nodeId))

    val result =
      if (lower.contains(nodeId)) {
        val (newLower, result) = lower.insert(tsContact)
        lower = newLower
        result
      } else {
        val (newGreater, result) = greater.insert(tsContact)
        greater = newGreater
        result
      }
    (this, result)
  }

  override def selectRandomNodeIDs(): Set[NodeID] = {
    lower.selectRandomNodeIDs() union greater.selectRandomNodeIDs()
  }

  protected def spaceClosestTo(nodeId: NodeID): IdDistanceSpace = {
    val distance = kadact.node.distance(origin, nodeId)
    if (lower.contains(nodeId) || distance < startDistance) {
      lower
    } else if (greater.contains(nodeId) || distance >= startDistance + range) {
      greater
    } else {
      throw new Exception("this should never happen")
    }
  }

  protected def otherHalf(halfSpace: IdDistanceSpace): IdDistanceSpace = {
    if (halfSpace == lower) {
      greater
    } else if (halfSpace == greater) {
      lower
    } else {
      throw new Exception("this should never happen")
    }
  }

  override def pickNNodesCloseTo(n: Int, nodeId: NodeID): Set[Contact] = {
    val space = spaceClosestTo(nodeId)
    val result = space.pickNNodesCloseTo(n, nodeId)

    if (result.size < n) {
      result union otherHalf(space).pickNNodesCloseTo(n - result.size, nodeId)
    } else {
      result
    }
  }

}

case class LeafIdSpace(override val origin: NodeID, override val depth: Int = 0, override val startDistance: Distance = 0)(implicit config: KadActConfig) extends IdDistanceSpace(origin, depth, startDistance) {
  val bucket: KBucket = new KBucket()

  protected def isSplittable: Boolean = {
    startDistance == 0
  }

  def insert(tsContact: TimestampedContact): (IdDistanceSpace, Boolean) = {
    val nodeId = tsContact.contact.nodeID
    //"[KademliaSpec] A node should never put its own NodeID into a bucket as a contact"
    require(nodeId != origin, "Cannot insert the contact referring to the origin node: " + origin)
    assert(this.contains(nodeId))
    val (inserted, _) = bucket.insertOrUpdate(tsContact)

    if (!inserted && isSplittable) {
      val newIDSpace = split()
      newIDSpace.insert(tsContact)
    } else {
      (this, false)
    }
  }

  def split(): SplittedIDSpace = {
    val lower = new LeafIdSpace(origin, depth + 1, startDistance)
    val greater = new LeafIdSpace(origin, depth + 1, startDistance + (range / 2))

    bucket.
    filter(contact => lower.contains(contact.nodeID)).
    foreach(lower.insert(_))

    bucket.
    filter(contact => greater.contains(contact.nodeID)).
    foreach(greater.insert(_))

    SplittedIDSpace(lower, greater)
  }

  override def pickNNodesCloseTo(n: Int, nodeId: NodeID): Set[Contact] = {
    assert(
      this.contains(nodeId)
    ) // this assertion may not hold, because we might have to look for Nodes when the nearest bucket did not have the required k entries
    bucket.pickNNodes(n)
  }

  override def selectRandomNodeIDs(): Set[NodeID] = {
    Set(randomNodeID())
  }
}