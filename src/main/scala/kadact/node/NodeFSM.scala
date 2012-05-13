package kadact.node

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM, ActorLogging, Props}
import akka.actor.Actor._
import akka.event.{Logging, LoggingReceive}

import scala.collection.immutable.HashMap
import scala.util.Random
import scala.math.BigInt

import kadact.KadAct
import kadact.node.routing.RoutingTable

object NodeFSM {
	val SHA1Hasher = java.security.MessageDigest.getInstance("SHA-1")
	private val random = new Random() 
	
	sealed trait State
	case object Uninitialized extends State
	case object Joining extends State
	case object AwaitingRoutingTableResponse extends State
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
	case object Start extends Messages
	case class Join(contact: Contact) extends Messages
	case object GetNodeID extends Messages
	case class StoreInNetwork[V](key: Key, value: V) extends Messages
	
	case class FindNode(from: Contact, generation: Int, nodeID: NodeID) extends Messages
	case class FindNodeResponse(from: Contact, generation: Int, contacts: Set[Contact]) extends Messages
	
	case class FindValue(from: Contact, generation: Int, key: Key) extends Messages
	case class FindValueResponse[V](from: Contact, generation: Int, answer: Either[V, Set[Contact]])
	
	case class Ping(from: Contact) extends Messages
	case class Pong(from: Contact) extends Messages
	
	case class Store[V](from: Contact, key: Key, value: V) extends Messages
	case class StoreResponse(from: Contact) extends Messages
	
	def generateNewNodeID: NodeID = {
		BigInt(KadAct.B, random)
	}
	
}

class NodeFSM[V](val nodeID: NodeID) extends Actor with FSM[NodeFSM.State, Map[Key,V]] with LoggingFSM[NodeFSM.State, Map[Key,V]] {
	import NodeFSM._
	import LookupManager._
	import routing.RoutingTable._
	import context._
	
	val selfContact: Contact = Contact(nodeID, self)
	
	val routingTable = actorOf(Props(new RoutingTable(selfContact)),"RoutingTable")
	val lookupManager = actorOf(Props(new LookupManager(selfContact, routingTable)),"LookupManager")
	
	//val storedValues = Map[Key, V]()
	
	def this() = this(NodeFSM.generateNewNodeID)

	def pickNNodesCloseTo(nodeID: NodeID) = {
		import akka.pattern.ask
		import akka.dispatch.Await
		import akka.util.Timeout
		import akka.util.Duration
		import akka.util.duration._
		
		Await.result((routingTable ? PickNNodesCloseTo(KadAct.k, nodeID))(30 seconds).mapTo[Set[Contact]], Duration.Inf)
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
		/*
		case store @ StoreInNetwork(key, value) => {
			val tmp = actorOf(new StoreInNetworkActor(selfContact, nodeLookupManager)).start()
			tmp.forward(store)
		}
		*/
		case Event(Store(from, key, value: V), storedValues) => {
			//storedValues += (key -> value)
			stay using (storedValues + (key -> value)) replying StoreResponse(selfContact)
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

/*

object StoreInNetworkActor {
	sealed trait State
	case object Start extends State
	case object Waiting extends State
}

class StoreInNetworkActor(originalNode: Contact, nodeLookupManager: ActorRef) extends Actor with FSM[StoreInNetworkActor.State,Option[(Key,Any)]]{
	import FSM._
	import StoreInNetworkActor._
	
	startWith(Start, None)
	
	when(Start){
		case Event(Node.StoreInNetwork(key, value),_) => {
			nodeLookupManager ! NodeLookupManager.Lookup(key)
			goto(Waiting) using(Some((key, value)))
		}
	}
	
	when(Waiting){
		case Event(NodeLookupManager.LookupResponse(nodeID, contacts), Some((key, value))) => {
			contacts foreach {
				_.node ! Node.Store(originalNode, key, value)
			}
			stop()
		}
	}
	
	
}
*/
