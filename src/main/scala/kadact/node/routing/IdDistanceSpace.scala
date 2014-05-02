package kadact.node.routing

import scala.util.Random
import kadact.node._
import kadact.config.KadActConfig
import kadact.node.Contact

abstract class IdDistanceSpace(val origin: NodeID, val depth: Int, val startDistance: Distance)(implicit config: KadActConfig) {
  val range = BigInt(2).pow(config.B - depth)
  val rand = new Random()
  val endDistance = startDistance + range

  def insert(contact: Contact): IdDistanceSpace = {
    insert(new TimestampedContact(System.currentTimeMillis(), contact))
  }

  def insert(tsContact: TimestampedContact): IdDistanceSpace

  def contains(nodeId: NodeID): Boolean = {
    val distance = kadact.node.distance(origin, nodeId)
    distance >= startDistance && distance < endDistance
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

case class SplittedIdSpace(var lower: IdDistanceSpace, var greater: IdDistanceSpace)(implicit config: KadActConfig)
  extends IdDistanceSpace(lower.origin, lower.depth - 1, lower.startDistance) {
  override def insert(tsContact: TimestampedContact): IdDistanceSpace = {
    val nodeId = tsContact.contact.nodeID
    assert(lower.contains(nodeId) || greater.contains(nodeId))

    if (lower.contains(nodeId)) {
      lower = lower.insert(tsContact)
    } else {
      greater = greater.insert(tsContact)
    }
    this
  }

  override def selectRandomNodeIDs(): Set[NodeID] = {
    lower.selectRandomNodeIDs() union greater.selectRandomNodeIDs()
  }

  protected def spaceClosestTo(nodeId: NodeID): IdDistanceSpace = {
    val distance = kadact.node.distance(origin, nodeId)
    if (lower.contains(nodeId) || distance < startDistance) {
      lower
    } else if (greater.contains(nodeId) || distance >= endDistance) {
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
    startDistance == BigInt(0)
  }

  def insert(tsContact: TimestampedContact): IdDistanceSpace = {
    val nodeId = tsContact.contact.nodeID
    //"[KademliaSpec] A node should never put its own NodeID into a bucket as a contact"
    require(nodeId != origin, "Cannot insert the contact referring to the origin node: " + origin)
    require(contains(nodeId), "Node "+ nodeId+" cannot be inserted in space range ["+startDistance+","+endDistance+"[")
    val (inserted, _) = bucket.insertOrUpdate(tsContact)

    if (!inserted && isSplittable) {
      val newIDSpace = split()
      newIDSpace.insert(tsContact)
    } else {
      this
    }
  }

  def split(): SplittedIdSpace = {
    val lower = new LeafIdSpace(origin, depth + 1, startDistance)
    val greater = new LeafIdSpace(origin, depth + 1, startDistance + (range / 2))

    // TODO: This is a bug. It will reset all the timestamps in the contacts
    bucket.filter(contact => lower.contains(contact.nodeID))
    .foreach(lower.insert(_))

    // TODO: This is a bug. It will reset all the timestamps in the contacts
    bucket.filter(contact => greater.contains(contact.nodeID))
    .foreach(greater.insert(_))

    SplittedIdSpace(lower, greater)
  }

  override def pickNNodesCloseTo(n: Int, nodeId: NodeID): Set[Contact] = {
    bucket.pickNNodes(n)
  }

  override def selectRandomNodeIDs(): Set[NodeID] = {
    Set(randomNodeID())
  }
}