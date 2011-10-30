package kadact

package object node {
	type NodeID = BigInt
	type Distance = BigInt
	
	def distance(node1: NodeID, node2: NodeID) : Distance = {
		node1 ^ node2
	}
}