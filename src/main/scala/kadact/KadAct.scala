package kadact

import akka.actor.Actor._
import akka.actor.ActorRef
import akka.util.duration._

import kadact.node.Node

object KadAct {
	import config.Config.config
	
	val alpha = config.getInt("kadact.alpha", 3)
	val B = config.getInt("kadact.B", 160)
	val k = config.getInt("kadact.k", 20)
	
	object Timeouts{
		val nodeLookup = config.getInt("kadact.timeouts.nodeLookup", 10).seconds
	}
	
}

class KadAct {
	import Node._
	import kadact.node._
	
	var internalNode : ActorRef = _
	
	def start(port: Int){
		require(port < 65536)
		
		internalNode = actorOf(new Node()).start()
		
		remote.start("localhost", port)
		remote.register("kadact-service",internalNode)
		
		internalNode ! Start
	}
	
	def join(host: String, port: Int){
		require(port < 65536)
		
		internalNode = actorOf(new Node()).start()
		
		remote.start("localhost", port+1)
		val remoteNode = remote.actorFor("kadact-service",host,port)
		val remoteNodeID = (remoteNode ? GetNodeID).as[NodeID].get
		
		internalNode ! Join(Contact(remoteNodeID, remoteNode))
	}
}