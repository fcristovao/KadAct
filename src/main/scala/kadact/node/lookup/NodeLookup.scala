package kadact.node.lookup

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM, ActorLogging, Props}
import akka.actor.Actor._
import kadact.KadAct
import kadact.node._
import kadact.config.KadActConfig

object NodeLookup {
	sealed trait State
	case object Idle extends State
	case object AwaitingContactsSet extends State
	case object AwaitingResponses extends State
	
	sealed trait Messages
	private case class Timeout(contact: Contact) extends Messages
	case class LookupNode(generation: Int, nodeID: NodeID) extends Messages
	case class LookupNodeResponse(generation: Int, nodeID: NodeID, contacts: Set[Contact]) extends Messages
	
	case class Data(nodeID: Option[GenericID] = None, generation: Int = -1, unasked: Set[Contact] = Set(), awaiting: Set[Contact] = Set(), failed: Set[Contact] = Set(), active: Set[Contact] = Set())
	
	val NullData = Data()
}

class NodeLookup(master: ActorRef, originalNode: Contact, routingTable: ActorRef)(implicit config: KadActConfig) extends Actor with FSM[NodeLookup.State, NodeLookup.Data] with LoggingFSM[NodeLookup.State, NodeLookup.Data]{
	import FSM._
	import NodeLookup._
	import routing.RoutingTable._
	import NodeFSM._
	import kadact.messages._
	
	//val generationIterator = Iterator from 0
	
	def broadcastFindNode(contacts: Set[Contact], generation: Int, nodeID: NodeID){
		for(contact <- contacts){
			setTimer(contact.nodeID.toString(), Timeout(contact), config.Timeouts.nodeLookup, false)
			contact.node ! FindNode(originalNode, generation, nodeID)
		}
	}
	
	def cancelRemainingTimers(awaitingForContacts: Set[Contact]){
		for(contact <- awaitingForContacts){
			cancelTimer(contact.nodeID.toString())
		}
	}
	
	startWith(Idle, NullData)
	
	when(Idle) {
		case Event(LookupNode(receivedGeneration, nodeID),_) => 
			routingTable ! PickNNodesCloseTo(config.alpha, nodeID)
			
			goto(AwaitingContactsSet) using Data(nodeID = Some(nodeID), generation = receivedGeneration)
	}
	
	when(AwaitingContactsSet) {
		case Event(contactsSet : Set[Contact], currentData @ Data(Some(nodeID), dataGeneration, _,_,_,_)) =>
		
		broadcastFindNode(contactsSet, dataGeneration, nodeID)
			
		goto(AwaitingResponses) using currentData.copy(awaiting = contactsSet)
	}
	
	when(AwaitingResponses) {
		case Event(FindNodeResponse(from, generation, contacts), currentData @ Data(Some(nodeID), dataGeneration, unasked, awaiting, failed, active)) if dataGeneration == generation && awaiting.contains(from) => {
			cancelTimer(from.nodeID.toString())

			routingTable ! Insert(from)
			
			val newActive = (active + from)
			val alreadyContacted = (newActive union awaiting union failed)
			val newUnasked = (((unasked union contacts) -- alreadyContacted) - originalNode) 
			/* ^-- we drop 'originalNode' because it is said that "The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor. 
			 * If the requestor does receive such a triple, it should discard it. A node must never put its own nodeID into a bucket as a contact."
			 */
			val contactsSet = (newUnasked -- alreadyContacted).take(1)
			val newAwaiting = ((awaiting - from) union contactsSet)
			//What happens when newAwaiting is empty? we should terminate and answer our master
			
			if(newActive.size == config.k || newAwaiting.isEmpty) {
				master ! LookupNodeResponse(generation, nodeID, newActive)
				cancelRemainingTimers(newAwaiting) // We've reached an answer, but we might already have sent requests to other nodes, that are now pending.
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
				master ! LookupNodeResponse(dataGeneration, nodeID, active)
				goto(Idle) using NullData
			} else {
				//here, contactsSet may be empty either, but this code will only result in emptying the awaiting set and filling up the failed. Must love higher order ops :)
				val newFailed = (failed + contact)
				val newUnasked = unasked -- contactsSet
				val newAwaiting = (awaiting - contact) union contactsSet
				
				broadcastFindNode(contactsSet, dataGeneration, nodeID)
				
				stay using currentData.copy(unasked = newUnasked, awaiting = newAwaiting, failed = newFailed)
			}
		}
	}
	
	initialize
}
