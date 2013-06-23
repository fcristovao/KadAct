package kadact.node

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM, ActorLogging, Props}
import akka.actor.Actor._
import akka.event.{Logging, LoggingReceive}
import scala.collection.immutable.HashMap
import scala.util.Random
import scala.math.BigInt
import kadact.KadAct
import kadact.node.routing.RoutingTable
import kadact.config.KadActConfig

object NodeFSM {
	val SHA1Hasher = java.security.MessageDigest.getInstance("SHA-1")
	private val random = new Random() 
	
	private[NodeFSM] sealed trait State
	private[NodeFSM] case object Uninitialized extends State
	private[NodeFSM] case object Joining extends State
	private[NodeFSM] case object AwaitingRoutingTableResponse extends State
	//horrible hack :S
	case object Initializing extends State {
		var msgsRemaining = 0
		
		def apply(msgsRemaining: Int) = {
			this.msgsRemaining = msgsRemaining
			this
		}
	}
	case object Working extends State
	
	/*
	sealed trait Data
	case class MessagesRemaining(n: Int) extends Data
	class StoredValues[V] extends HashMap[Key,V] with Data
	*/
	
	sealed trait Messages
	
	sealed trait InterfaceMessages extends Messages
	
	case object Start extends InterfaceMessages
	case class Join(contact: Contact) extends InterfaceMessages
	case object GetNodeID extends InterfaceMessages
	case class AddToNetwork[V](key: Key, value: V) extends InterfaceMessages
	case class GetFromNetwork[V](key: Key) extends InterfaceMessages
	case object Done extends InterfaceMessages
	
	sealed class ProtocolMessages(from: Contact, generation: Int) extends Messages
	
	case class FindNode(from: Contact, generation: Int, nodeID: NodeID) extends ProtocolMessages(from, generation)
	case class FindNodeResponse(from: Contact, generation: Int, contacts: Set[Contact]) extends ProtocolMessages(from, generation)
	
	case class FindValue(from: Contact, generation: Int, key: Key) extends ProtocolMessages(from, generation)
	case class FindValueResponse[V](from: Contact, generation: Int, answer: Either[V, Set[Contact]]) extends ProtocolMessages(from, generation)
	
	case class Ping(from: Contact, generation: Int) extends ProtocolMessages(from, generation)
	case class Pong(from: Contact, generation: Int) extends ProtocolMessages(from, generation)
	
	case class Store[V](from: Contact, generation: Int, key: Key, value: V) extends ProtocolMessages(from, generation)
	case class StoreResponse(from: Contact, generation: Int) extends ProtocolMessages(from, generation)
	
	def generateNewNodeID(implicit config: KadActConfig): NodeID = {
		BigInt(config.B, random)
	}
	
}

class NodeFSM[V](val nodeID: NodeID)(implicit config: KadActConfig) extends Actor with FSM[NodeFSM.State, Map[Key,V]] with LoggingFSM[NodeFSM.State, Map[Key,V]] {
	import NodeFSM._
	import lookup.LookupManager
	import lookup.LookupManager._
	import routing.RoutingTable._
	import context._
	
	val selfContact: Contact = Contact(nodeID, self)
	val generationIterator = Iterator from 0
	val routingTable = actorOf(Props(new RoutingTable(selfContact)),"RoutingTable")
	val lookupManager = actorOf(Props(new LookupManager(selfContact, routingTable)),"LookupManager")
	
	def this()(implicit config: KadActConfig) = this(NodeFSM.generateNewNodeID)

	def pickNNodesCloseTo(nodeID: NodeID) = {
		import akka.pattern.ask
		import scala.concurrent.Await
		import akka.util.Timeout
		import scala.concurrent.duration._
		
		//The hardcoded 30 seconds is just a formality. We expect that we'll never need 30 seconds to get a response from the routing table
		Await.result((routingTable ? PickNNodesCloseTo(config.k, nodeID))(30 seconds).mapTo[Set[Contact]], Duration.Inf)
	}
	
	startWith(Uninitialized, Map[Key,V]())
	
	when(Uninitialized){
		case Event(Start, _) => {
			log.info("Started KadAct Node with ID: "+nodeID)
			goto (NodeFSM.Working)
		}
		
		case Event(Join(contact), _) => {
			log.info("Started KadAct Node with ID: "+nodeID)
			routingTable ! Insert(contact)
			
			lookupManager ! LookupNode(nodeID)
			
			goto(Joining)
		}
	}
	
	when(Joining){
		case Event(LookupNodeResponse(nodeID, _), _) if this.nodeID == nodeID => {
			routingTable ! SelectRandomIDs
			goto(AwaitingRoutingTableResponse)
		}
	}
	
	when(AwaitingRoutingTableResponse){
		case Event(setOfNodeIDs: Set[NodeID], _) => {
			log.debug("Random IDs: "+setOfNodeIDs)
			
			for(nodeID <- setOfNodeIDs){
				lookupManager ! LookupNode(nodeID)
			}
			
			goto(Initializing(setOfNodeIDs.size)) 
		}
	}
	
	when(Initializing){
		case Event(LookupNodeResponse(_,_), _) => {
			if(Initializing.msgsRemaining > 1)
				goto(Initializing(Initializing.msgsRemaining - 1))
			else
				goto(NodeFSM.Working)
		}
		
		case Event(FindNode(fromContact, generation, nodeID), _) => {
			routingTable ! Insert(fromContact)
			val contactsSet = pickNNodesCloseTo(nodeID)
			log.debug(FindNodeResponse(this.selfContact, generation, contactsSet).toString())
			
			stay replying FindNodeResponse(this.selfContact, generation, contactsSet)
		}
	}

	when(NodeFSM.Working){
		case Event(FindNode(fromContact, generation, nodeID), _) => {
			routingTable ! Insert(fromContact)
			val contactsSet = pickNNodesCloseTo(nodeID) - fromContact
			// ^-- we remove 'fromContact' because it is said that "The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor."
			log.debug(FindNodeResponse(this.selfContact, generation, contactsSet).toString())
			
			stay replying FindNodeResponse(this.selfContact, generation, contactsSet)
		}
		
		case Event(msg @ AddToNetwork(key, value),_) => {
			val nextGen = generationIterator.next()
			val tmp = actorOf(Props(new AddToNetworkActor(selfContact, nextGen, routingTable, lookupManager)),"AddToNetworkActor"+nextGen+"")
			tmp.forward(msg)
			stay
		}
		
		case Event(Store(fromContact, generation, key, value: V), storedValues) => {
			routingTable ! Insert(fromContact)
			stay using (storedValues + (key -> value)) replying StoreResponse(selfContact, generation)
		}
		
		case Event(FindValue(fromContact, generation, key), storedValues) => {
			routingTable ! Insert(fromContact)
			
			storedValues.get(key) match {
				case None => {
					val contactsSet = pickNNodesCloseTo(nodeID) - fromContact
					// ^-- we remove 'fromContact' because it is said that "The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor."
					log.debug(FindValueResponse(this.selfContact, generation, Right(contactsSet)).toString())
					stay replying FindValueResponse(this.selfContact, generation, Right(contactsSet))
				}
				case Some(value) => {
					log.debug(FindValueResponse(this.selfContact, generation, Left(value)).toString())
					stay replying FindValueResponse(this.selfContact, generation, Left(value))
				}
			}
			
			
		}

	}
	
	whenUnhandled{
		case Event(GetNodeID,_) =>
			stay replying nodeID
	}
	
	initialize
	
	override def toString: String = {
		"KadAct-Node("+nodeID+")"
	}
}
