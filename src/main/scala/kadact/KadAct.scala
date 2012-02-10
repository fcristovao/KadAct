package kadact

import akka.actor.Actor._
import akka.actor.ActorRef
import akka.util.duration._

import kadact.node.Node

/** Implementation of the Kademlia P2P network
  * "Kademlia: A Peer-to-peer Information System Based on the XOR Metric" (2002)
  * http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.16.4785
  * http://people.cs.aau.dk/~bnielsen/DSE04/papers/kademlia.pdf
  * 
  * "S/Kademlia: A practicable approach towards secure key-based routing" (2007)
  * http://ieeexplore.ieee.org/xpl/freeabs_all.jsp?arnumber=4447808
  * https://doc.tm.uka.de/SKademlia_2007.pdf
  * 
  * "R/Kademlia: Recursive and topology-aware overlay routing" (2010)
  * http://ieeexplore.ieee.org/xpl/freeabs_all.jsp?arnumber=5680244
  * http://telematics.tm.kit.edu/publications/Files/416/RKademlia_2010.pdf
  * 
  * "Improving the Performance and Robustness of Kademlia-based Overlay Networks" (2007)
  * http://www.springerlink.com/content/v06258j277t27066/
  * http://www-info3.informatik.uni-wuerzburg.de/TR/tr405.pdf
  */
object KadAct {
	import config.Config.config
	
	val alpha = config.getInt("kadact.alpha", 3)
	val B = config.getInt("kadact.B", 160)
	val k = config.getInt("kadact.k", 20)
	val s = config.getInt("kadact.s", 20)
	
	object Timeouts{
		val nodeLookup = config.getInt("kadact.timeouts.nodeLookup", 10).seconds
	}
	
	val maxParallelLookups = 3
	
}

class KadAct[V] {
	import Node._
	import kadact.node._
	
	var internalNode : ActorRef = _
	
	def start(port: Int){
		require(port < 65536)
		
		internalNode = actorOf(new Node[V])
		
		remote.start("localhost", port)
		remote.register("kadact-service",internalNode)
		internalNode ! Start
	}
	
	def join(remoteHost: String, remotePort: Int, localPort: Int){
		require(remotePort < 65536 && localPort < 65536)
		
		internalNode = actorOf(new Node()).start()
		
		remote.start("localhost", localPort)
		remote.register("kadact-service",internalNode)
		val remoteNode = remote.actorFor("kadact-service", remoteHost, remotePort)
		val remoteNodeID = (remoteNode ? GetNodeID).as[NodeID].get
		
		internalNode.start()
		internalNode ! Join(Contact(remoteNodeID, remoteNode))
	}
	
	def add(key: Key, value: V) = {
		internalNode ! StoreInNetwork[V](key, value)
		
	}
}