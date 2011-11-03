package kadact.node

import akka.actor.{Actor, ActorRef, FSM}
import akka.actor.Actor._
import akka.util.Duration
import kadact.KadAct

object NodeLookup {
	sealed trait State
	object Idle extends State
	object AwaitingResponses extends State
	
	sealed trait Messages
	case class Lookup(nodeID: NodeID) extends Messages
	case class Timeout(contact: Contact) extends Messages
	case class LookupResponse(nodeID: NodeID, contacts: Set[Contact]) extends Messages
	
	case class Data(nodeID: Option[NodeID] = None, generation: Int = -1, unasked: Set[Contact] = Set(), awaiting: Set[Contact] = Set(), failed: Set[Contact] = Set(), active: Set[Contact] = Set())
	
	val NullData = Data()
}

class NodeLookup(master: Contact, routingTable: ActorRef) extends Actor with FSM[NodeLookup.State, NodeLookup.Data] {
	import FSM._
	import NodeLookup._
	import RoutingTable._
	import Node._
	
	val generationIterator = Iterator from 0
	
	def broadcastFindNode(contacts: Set[Contact], generation: Int, nodeID: NodeID){
		for(contact <- contacts){
			setTimer(contact.nodeID.toString(), Timeout(contact), KadAct.Timeouts.nodeLookup, false)
			contact.node ! FindNode(master, generation, nodeID)
		}
	}
	
	startWith(Idle, NullData)
	
	when(Idle) {
		case Ev(Lookup(nodeID)) => 
			val contactsSet = routingTable.?(PickNNodesCloseTo(KadAct.alpha, nodeID))(timeout = Duration.Inf).as[Set[Contact]].get
			val nextGen = generationIterator.next()
			
			broadcastFindNode(contactsSet, nextGen, nodeID)
			
			goto(AwaitingResponses) using Data(generation = nextGen, awaiting = contactsSet)
	}
	
	when(AwaitingResponses) {
		case Event(FindNodeResponse(from, generation, contacts), currentData @ Data(Some(nodeID), dataGeneration, unasked, awaiting, failed, active)) if dataGeneration == generation && awaiting.contains(from) => {
			cancelTimer(from.toString())
			
			val newActive = (active + from)
			
			if(newActive.size == KadAct.k) {
				master.node ! LookupResponse(nodeID, newActive)
				goto(Idle) using NullData
			} else {
				
				val newUnasked = (unasked union contacts)
				val contactsSet = (newUnasked diff (awaiting union failed union active)).take(1)
				val newAwaiting = ((awaiting - from) union contactsSet)
				
				//What happens when newAwaiting is empty? we should terminate and answer our master
				
				broadcastFindNode(contactsSet, generation, nodeID)
				
				stay using currentData.copy(unasked = newUnasked, awaiting = newAwaiting, active = newActive)
			}
		}
		case Event(Timeout(contact), currentData @ Data(Some(nodeID), dataGeneration, unasked, awaiting, failed, active)) if awaiting.contains(contact) => {
			val newFailed = (failed + contact)
			
			stay
		}
	}
	
	
}
