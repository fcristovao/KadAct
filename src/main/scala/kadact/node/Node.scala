package kadact.node

import akka.actor.{Actor, ActorRef, FSM}
import akka.actor.Actor._
import akka.util.Duration

import scala.util.Random
import scala.math.BigInt

import kadact.KadAct


object Node {
	val SHA1Hasher = java.security.MessageDigest.getInstance("SHA-1")
	private val random = new Random() 
	
	sealed trait Messages
	case object Start extends Messages
	case class Join(contact: Contact) extends Messages
	case object GetNodeID extends Messages
	case class FindNode(from: Contact, generation: Int, nodeID: NodeID) extends Messages
	case class FindNodeResponse(from: Contact, generation: Int, contacts: Set[Contact]) extends Messages
	
	def generateNewNodeID: NodeID = {
		BigInt(KadAct.B, random)
	}
	
}

class Node(val nodeID: NodeID) extends Actor {
	import Node._
	import RoutingTable._
	
	val selfContact = Contact(nodeID, self)
	var pendingNodeLookups = Map[Int, Set[Contact]]()
	
	val generation = Iterator from 0
	val routingTable = actorOf(new RoutingTable(selfContact))
	
	def this() = this(Node.generateNewNodeID)
	
	def beforeStarted: Receive = {
		case Start => println("Started")
		case m @ Join(contact) => 
			routingTable ! Insert(contact)
			
		case GetNodeID => self.reply(nodeID)
		case "hello" => println("got it")
		case anyOther => self.reply("hello")
		
	}
	
	def startNodeLookup(nodeID: NodeID) = {
		val newGeneration = generation.next()
		
		//routingTable.pickAlphaNodesCloseTo(nodeID).foreach(_.node ! FindNode(newGeneration, nodeID))
	}
	
	def normal: Receive = {
		case FindNode(_, generation, nodeID) => {
			val contactsSet = routingTable.?(PickNNodesCloseTo(KadAct.k, nodeID))(timeout = Duration.Inf).as[Set[Contact]].get
			self.reply(FindNodeResponse(selfContact, generation, contactsSet))
		}
	}
	
	def receive = beforeStarted
}
