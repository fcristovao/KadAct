/*
package kadact.node

import akka.actor.{Actor, ActorRef, FSM, ActorLogging, Props}
import akka.actor.Actor._
import akka.util.Duration
import akka.event.{Logging, LoggingReceive}

import scala.collection.mutable.Map
import scala.util.Random
import scala.math.BigInt

import kadact.KadAct
import kadact.node.routing.RoutingTable

object Node {
	val SHA1Hasher = java.security.MessageDigest.getInstance("SHA-1")
	private val random = new Random() 
	
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

class Node[V](val nodeID: NodeID) extends Actor with ActorLogging {
	import Node._
	import LookupManager._
	import routing.RoutingTable._
	import context._
	
	val selfContact: Contact = Contact(nodeID, self)
	
	val routingTable = actorOf(Props(new RoutingTable(selfContact)))
	val lookupManager = actorOf(Props(new LookupManager(selfContact, routingTable)))
	
	val storedValues = Map[Key, V]()
	
	def this() = this(Node.generateNewNodeID)

	
	def beforeStarted: Receive = LoggingReceive{
		case Start => {
			log.info("Started KadAct Node with ID: "+nodeID)
			become(normal)
		}
		
		case m @ Join(contact) => {
			log.info("Started KadAct Node with ID: "+nodeID)
			routingTable ! Insert(contact)
			
			lookupManager ! LookupNode(nodeID)
		}
		
		case LookupNodeResponse(nodeID, _) if this.nodeID == nodeID => { 
			// IT CAN HAPPEN THAT THE SET RETURNED is empty (when the node we contacted never answered)!!! (must check this case!!!)
			val setOfNodeIDs = routingTable.?(SelectRandomIDs)/*(timeout = Duration.Inf)*/.as[Set[NodeID]].get
			
			
			EventHandler.debug(self, "Random IDs: "+setOfNodeIDs)
			for(nodeID <- setOfNodeIDs){
				nodeLookupManager ! Lookup(nodeID)
			}
			become(initializing(setOfNodeIDs.size))
		}
	}
	
	def initializing(maxMessages: Int): Receive = loggable(self){
		case LookupResponse(_, _) => {
			//self.sender.get.stop()
			if(maxMessages>1)
				become(initializing(maxMessages-1))
			else{
				become(normal)
			}
		}
		case FindNode(fromContact, generation, nodeID) => {
			routingTable ! Insert(fromContact)
			val contactsSet = routingTable.?(PickNNodesCloseTo(KadAct.k, nodeID))/*(timeout = Duration.Inf)*/.as[Set[Contact]].get
			EventHandler.debug(self, FindNodeResponse(this.selfContact, generation, contactsSet))
			self.reply(FindNodeResponse(this.selfContact, generation, contactsSet))
		}
		case GetNodeID => self.reply(nodeID)
	}
	
	
	def normal: Receive = loggable(self){
		case FindNode(fromContact, generation, nodeID) => {
			routingTable ! Insert(fromContact)
			val contactsSet = ((routingTable.?(PickNNodesCloseTo(KadAct.k, nodeID))/*(timeout = Duration.Inf)*/.as[Set[Contact]].get) - fromContact)
			/* ^-- we remove 'fromContact' because it is said that "The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor."
			 */
			EventHandler.debug(self, FindNodeResponse(this.selfContact, generation, contactsSet))
			self.reply(FindNodeResponse(this.selfContact, generation, contactsSet))
		}
		case GetNodeID => self.reply(nodeID)
		case store @ StoreInNetwork(key, value) => {
			val tmp = actorOf(new StoreInNetworkActor(selfContact, nodeLookupManager)).start()
			tmp.forward(store)
		}
		case Store(from, key, value: V) => {
			storedValues += (key -> value)
			self.reply(StoreResponse(selfContact))
		}
		case FindValue(fromContact, generation, key) => {
			routingTable ! Insert(fromContact)
			
			storedValues.get(key) match {
				case None => {
					val contactsSet = ((routingTable.?(PickNNodesCloseTo(KadAct.k, nodeID))/*(timeout = Duration.Inf)*/.as[Set[Contact]].get) - fromContact)
					/* ^-- we remove 'fromContact' because it is said that "The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor."
					 */
					EventHandler.debug(self, FindValueResponse(this.selfContact, generation, Right(contactsSet)))
					self.reply(FindValueResponse(this.selfContact, generation, Right(contactsSet)))
				}
				case Some(value) => {
					EventHandler.debug(self, FindValueResponse(this.selfContact, generation, Left(value)))
					self.reply(FindValueResponse(this.selfContact, generation, Left(value)))
				}
			}
			
			
		}
	}
	
	def receive = beforeStarted
	
	override def toString: String = {
		"KadAct-Node("+nodeID+")"
	}
}

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
