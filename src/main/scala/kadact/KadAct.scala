package kadact

import akka.actor.Actor._
import akka.actor.ActorRef
import scala.concurrent.duration._
import kadact.node.KadActNode
import akka.actor.ActorSystem
import akka.actor.Props
import scala.concurrent.Await
import akka.util.Timeout
import kadact.config.KadActConfig

/** 
 * Implementation of the Kademlia P2P network with the Actors Model (with the Akka Library)
 * 
 * [Maymounkov2002]: "Kademlia: A Peer-to-peer Information System Based on the XOR Metric" (2002)
 * http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.16.4785
 * http://people.cs.aau.dk/~bnielsen/DSE04/papers/kademlia.pdf
 * 
 * [KademliaSpec]: Kademlia Specification:
 * http://xlattice.sourceforge.net/components/protocol/kademlia/specs.html
 * 
 * [Baumgart2007]: "S/Kademlia: A practicable approach towards secure key-based routing" (2007)
 * http://ieeexplore.ieee.org/xpl/freeabs_all.jsp?arnumber=4447808
 * https://doc.tm.uka.de/SKademlia_2007.pdf
 * http://telematics.tm.kit.edu/publications/Files/267/SKademlia_slides_2007.pdf
 * 
 * [Heep2010]: "R/Kademlia: Recursive and topology-aware overlay routing" (2010)
 * http://ieeexplore.ieee.org/xpl/freeabs_all.jsp?arnumber=5680244
 * http://telematics.tm.kit.edu/publications/Files/416/RKademlia_2010.pdf
 * 
 * [Binzenhöfer2007]: "Improving the Performance and Robustness of Kademlia-based Overlay Networks" (2007)
 * http://www.springerlink.com/content/v06258j277t27066/
 * http://www-info3.informatik.uni-wuerzburg.de/TR/tr405.pdf
 */
class KadAct[V](hostname: String, localPort: Int)(implicit kadActConfig: KadActConfig) {
	import KadActNode._
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
	
	val kadActSys = ActorSystem("KadActSystem"+localPort, customConf.withFallback(config))
	val internalNode = kadActSys.actorOf(Props[KadActNode[V]],"KadActNode")
	
	def start(){
		val timeout = kadActConfig.Timeouts.storeValue
		Await.ready((internalNode ? Start)(Timeout(timeout)), timeout)
	}
	
	def join(remoteHost: String, remotePort: Int){
		require(remotePort < 65536)
		implicit val timeout = kadActConfig.Timeouts.nodeLookup
		
		val remoteNode = kadActSys.actorFor("akka://KadActSystem"+remotePort+"@"+remoteHost+":"+remotePort+"/user/KadActNode")
		
		println("Connected: "+(!remoteNode.isTerminated))
		
		val remoteNodeID = Await.result((remoteNode ? GetContact)(Timeout(timeout)).mapTo[NodeID], timeout)
		
		Await.ready((internalNode ? Join(Contact(remoteNodeID, remoteNode)))(Timeout(timeout)), timeout) 
	}
	
	def add(key: Key, value: V) = {
		val timeout = kadActConfig.Timeouts.storeValue
		Await.ready((internalNode ? AddToNetwork[V](key, value))(Timeout(timeout)), timeout)
		
	}
	
	def get(key: Key): Option[V] = {
		//Await.result()internalNode ? GetFromNetwork(key)
		None
	}
	
	def stop() = {
		kadActSys.shutdown()
	}
}