package kadact

import akka.actor.Actor._

import kadact.node.Node

object KadAct {
	import config.Config.config
	
	val alpha = config.getInt("kadact.alpha", 3)
	val B = config.getInt("kadact.B", 160)
	val k = config.getInt("kadact.k", 20)
	
}

class KadAct {
	def start(port: Int){
		require(port < 65536)
		
		remote.start("localhost", port)
		remote.register("kadact-service",actorOf(new Node()))
	}
	
	def join(host: String, port: Int){
		require(port < 65536)
		
		println("test")
		
		val remoteNode = remote.actorFor("kadact-service",host,port)
		remoteNode.start()
		println((remoteNode ? "hello").as[String].get)
	}
}