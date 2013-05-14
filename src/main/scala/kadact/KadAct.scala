package kadact

import akka.actor.Actor._
import akka.actor.ActorRef
import scala.concurrent.duration._
import kadact.node.NodeFSM
import akka.actor.ActorSystem
import akka.actor.Props
import scala.concurrent.Await
import akka.util.Timeout

/** Implementation of the Kademlia P2P network
  * "Kademlia: A Peer-to-peer Information System Based on the XOR Metric" (2002)
  * http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.16.4785
  * http://people.cs.aau.dk/~bnielsen/DSE04/papers/kademlia.pdf
  * 
  * "S/Kademlia: A practicable approach towards secure key-based routing" (2007)
  * http://ieeexplore.ieee.org/xpl/freeabs_all.jsp?arnumber=4447808
  * https://doc.tm.uka.de/SKademlia_2007.pdf
  * http://telematics.tm.kit.edu/publications/Files/267/SKademlia_slides_2007.pdf
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
	import com.typesafe.config.ConfigFactory
	import collection.JavaConversions._
	
	val config = ConfigFactory.parseMap(Map("kadact.alpha" -> 3, "kadact.B" -> 4, "kadact.k" -> 2, "kadact.s" -> 1, "kadact.timeouts.nodeLookup" -> 5))
	
	val alpha = config.getInt("kadact.alpha")//, 3)
	val B = config.getInt("kadact.B")//, 160)
	val k = config.getInt("kadact.k")//, 20)
	val s = config.getInt("kadact.s")//, 20)
	
	object Timeouts{
		val nodeLookup = config.getInt("kadact.timeouts.nodeLookup").seconds//, 10).seconds
		val storeValue = 5 seconds
	}
	
	val maxParallelLookups = 3
	
}

class KadAct[V](hostname: String, localPort: Int) {
	import NodeFSM._
	import kadact.node._
	import akka.pattern.ask
	import com.typesafe.config.ConfigFactory

	require(localPort < 65536)

	val config = ConfigFactory.load()
	val customConf = ConfigFactory.parseString("""
      akka.remote {
				transport = "akka.remote.netty.NettyRemoteTransport"
				netty {
					hostname = "%s"
					port = %d
				}
			}
      """.format(hostname,localPort))
	
	val kadActSys = ActorSystem("KadActSystem", customConf.withFallback(config))
	val internalNode = kadActSys.actorOf(Props(new NodeFSM[V]),"KadActNode")
	
	def start(){
		internalNode ! Start
	}
	
	def join(remoteHost: String, remotePort: Int){
		require(remotePort < 65536)
		implicit val timeout = KadAct.Timeouts.nodeLookup
		
		val remoteNode = kadActSys.actorFor("akka://KadActSystem@"+remoteHost+":"+remotePort+"/user/KadActNode")
		
		println("Connected: "+(!remoteNode.isTerminated))
		
		val remoteNodeID = Await.result((remoteNode ? GetNodeID)(Timeout(timeout)).mapTo[NodeID], timeout)
		
		internalNode ! Join(Contact(remoteNodeID, remoteNode))
	}
	
	def add(key: Key, value: V) = {
		internalNode ! AddToNetwork[V](key, value)
	}
	
	def get(key: Key): Option[V] = {
		//Await.result()internalNode ? GetFromNetwork(key)
		None
	}
}