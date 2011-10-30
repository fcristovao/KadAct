package kadact.node

import akka.actor.{Actor, ActorRef}

import scala.collection.immutable.SortedSet
import scala.util.Random

import kadact.KadAct

object Node {
	sealed trait Messages
	
}

class Node(var nodeID: Option[NodeID] = None) extends Actor {
	val sha1Hasher = java.security.MessageDigest.getInstance("SHA-1")
	
	override def preStart() = {
		if(nodeID.isEmpty){
			nodeID = Some(BigInt(sha1Hasher.digest(self.getUuid().toString().getBytes())))
		}
	}
	
	def receive = {
		case msg => self.reply("world")
	}
}
