package kadact.node

import akka.actor.{Actor, ActorRef, FSM}
import akka.actor.Actor._
import akka.util.Duration
import kadact.KadAct
import akka.actor.LoggingFSM

//There should be a NodeLookupManager to deal with the creation of NodeLookups

object NodeLookup {
	sealed trait State
	case object Idle extends State
	case object AwaitingResponses extends State
	
	sealed trait Messages
	case class Lookup(generation: Int, nodeID: NodeID) extends Messages
	case class Timeout(contact: Contact) extends Messages
	case class LookupResponse(generation: Int, nodeID: NodeID, contacts: Set[Contact]) extends Messages
	
	case class Data(nodeID: Option[NodeID] = None, generation: Int = -1, unasked: Set[Contact] = Set(), awaiting: Set[Contact] = Set(), failed: Set[Contact] = Set(), active: Set[Contact] = Set())
	
	val NullData = Data()
}

class NodeLookup(master: ActorRef, originalNode: Contact, routingTable: ActorRef) extends Actor with FSM[NodeLookup.State, NodeLookup.Data] with LoggingFSM[NodeLookup.State, NodeLookup.Data]{
	import FSM._
	import NodeLookup._
	import routing.RoutingTable._
	import Node._
	
	//val generationIterator = Iterator from 0
	
	def broadcastFindNode(contacts: Set[Contact], generation: Int, nodeID: NodeID){
		for(contact <- contacts){
			setTimer(contact.nodeID.toString(), Timeout(contact), KadAct.Timeouts.nodeLookup, false)
			contact.node ! FindNode(originalNode, generation, nodeID)
		}
	}
	
	startWith(Idle, NullData)
	
	when(Idle) {
		case Ev(Lookup(receivedGeneration, nodeID)) => 
			val contactsSet = routingTable.?(PickNNodesCloseTo(KadAct.alpha, nodeID))/*(timeout = Duration.Inf)*/.as[Set[Contact]].get
			//val nextGen = generationIterator.next()
			
			broadcastFindNode(contactsSet, receivedGeneration, nodeID)
			
			goto(AwaitingResponses) using Data(nodeID = Some(nodeID), generation = receivedGeneration, awaiting = contactsSet)
	}
	
	when(AwaitingResponses) {
		case Event(FindNodeResponse(from, generation, contacts), currentData @ Data(Some(nodeID), dataGeneration, unasked, awaiting, failed, active)) if dataGeneration == generation && awaiting.contains(from) => {
			cancelTimer(from.nodeID.toString())

			routingTable ! Insert(from)
			
			val newActive = (active + from)
			val newUnasked = ((unasked union contacts) - originalNode) 
			/* ^-- we drop the master because it is said that "The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor. 
			 * If the requestor does receive such a triple, it should discard it. A node must never put its own nodeID into a bucket as a contact."
			 */
			val contactsSet = (newUnasked diff (awaiting union failed union active)).take(1)
			val newAwaiting = ((awaiting - from) union contactsSet)
			//What happens when newAwaiting is empty? we should terminate and answer our master
			
			if(newActive.size == KadAct.k || newAwaiting.isEmpty) {
				master ! LookupResponse(generation, nodeID, newActive)
				goto(Idle) using NullData
			} else {
				broadcastFindNode(contactsSet, generation, nodeID)
				
				stay using currentData.copy(unasked = newUnasked, awaiting = newAwaiting, active = newActive)
			}
		}
		case Event(Timeout(contact), currentData @ Data(Some(nodeID), dataGeneration, unasked, awaiting, failed, active)) if awaiting.contains(contact) => {
			val contactsSet = (unasked).take(1)

			if(contactsSet.isEmpty && awaiting.size == 1){
				//there's no one else to contact and we were the only ones left
				master ! LookupResponse(dataGeneration, nodeID, active)
				goto(Idle) using NullData
			} else {
				//here, contactsSet may be empty either, but this code will only result in emptying the awaiting set an filling up the failed. Must love higher order ops :)
				val newFailed = (failed + contact)
				val newUnasked = unasked diff contactsSet
				val newAwaiting = (awaiting - contact) union contactsSet
				
				broadcastFindNode(contactsSet, dataGeneration, nodeID)
				
				stay using currentData.copy(unasked = newUnasked, awaiting = newAwaiting, failed = newFailed)
			}
		}
	}
	
	initialize
}
