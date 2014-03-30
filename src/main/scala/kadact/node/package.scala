package kadact

package object node {

  type GenericID = BigInt
  type NodeID = GenericID
  type Distance = BigInt
  type Key = GenericID

  def distance(node1: NodeID, node2: NodeID): Distance = {
    node1 ^ node2
  }
}