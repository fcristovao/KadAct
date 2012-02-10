package kadact.node

import akka.actor.ActorRef
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.ObjectInputStream

import akka.serialization.RemoteActorSerialization._

case class Contact(var nodeID: NodeID, var node: ActorRef) {
	
	/*
	private def nodeID_=(newNodeID: NodeID) : Unit = {
		nodeID = newNodeID 
	}
	*/
	
	@throws(classOf[IOException])
	private def writeObject(out: ObjectOutputStream){
		out.writeObject(nodeID)
		val tmp = toRemoteActorRefProtocol(node).toByteArray()
		out.writeInt(tmp.size)
		out.write(tmp)
	}
	
	@throws(classOf[IOException]) @throws(classOf[ClassNotFoundException])
	private def readObject(in: ObjectInputStream){
		nodeID = in.readObject().asInstanceOf[NodeID];
		val tmp = in.readInt()
		val tmpArray = new Array[Byte](tmp)
		in.read(tmpArray,0,tmp)
		node = fromBinaryToRemoteActorRef(tmpArray)
	}
	
	override def toString(): String = {
		"Contact("+nodeID+")"
	}
	
	override def equals(that: Any): Boolean = {
		that match {
			case Contact(nodeID,_) => this.nodeID.equals(nodeID)
			case _ => false
		}
	}
	
	override def hashCode(): Int = {
		nodeID.hashCode()
	}
}

private[node] case class ContactClosestToOrdering(origin: NodeID) extends Ordering[Contact] {
	override def compare(x: Contact, y: Contact) = {
		val distanceX = distance(x.nodeID, origin)
		val distanceY = distance(y.nodeID, origin)
		
		if(distanceX == distanceY){
			0
		} else if (distanceX < distanceY) {
			-1
		} else {
			1
		}
	}
}