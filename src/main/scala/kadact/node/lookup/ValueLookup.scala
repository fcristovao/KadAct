package kadact.node.lookup

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM, ActorLogging, Props}
import akka.actor.Actor._
import akka.event.{Logging, LoggingReceive}
import kadact.KadAct
import kadact.node._
import kadact.config.KadActConfig

//There should be a NodeLookupManager to deal with the creation of NodeLookups

object ValueLookup {
	sealed trait State
	case object Idle extends State
	case object AwaitingResponses extends State
	
	sealed trait Messages
	case class Timeout(contact: Contact) extends Messages
	case class LookupValue(generation: Int, key: Key) extends Messages
	case class LookupValueResponse[V](generation: Int, key: Key, answer: Either[V, Set[Contact]]) extends Messages
	
	case class Data(key: Option[Key] = None, generation: Int = -1, unasked: Set[Contact] = Set(), awaiting: Set[Contact] = Set(), failed: Set[Contact] = Set(), active: Set[Contact] = Set())
	
	val NullData = Data()
}

class ValueLookup(master: ActorRef, originalNode: Contact, routingTable: ActorRef)(implicit config: KadActConfig) extends Actor with FSM[ValueLookup.State, ValueLookup.Data] with LoggingFSM[ValueLookup.State, ValueLookup.Data]{
	import FSM._
	import ValueLookup._
	import routing.RoutingTable._
	import NodeFSM._
	import kadact.messages._
	
	def broadcastFindValue(contacts: Set[Contact], generation: Int, key: Key){
		for(contact <- contacts){
			setTimer(contact.nodeID.toString(), Timeout(contact), config.Timeouts.nodeLookup, false)
			contact.node ! FindValue(originalNode, generation, key)
		}
	}
	
	startWith(Idle, NullData)
	
	
	when(Idle){
		case _ => stay
	}
	/*
	when(Idle) {
		case Ev(LookupValue(receivedGeneration, key)) => 
			val contactsSet = routingTable.?(PickNNodesCloseTo(KadAct.alpha, key))/*(timeout = Duration.Inf)*/.as[Set[Contact]].get
			
			broadcastFindValue(contactsSet, receivedGeneration, key)
			
			goto(AwaitingResponses) using Data(key = Some(key), generation = receivedGeneration, awaiting = contactsSet)
	}
	
	when(AwaitingResponses) {
		case Event(FindValueResponse(from, generation, Right(contacts)), currentData @ Data(Some(key), dataGeneration, unasked, awaiting, failed, active)) if dataGeneration == generation && awaiting.contains(from) => {
			cancelTimer(from.nodeID.toString())

			routingTable ! Insert(from)
			
			val newActive = (active + from)
			val newUnasked = ((unasked union contacts) - originalNode) 
			/* ^-- we drop 'originalNode' because it is said that "The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor. 
			 * If the requestor does receive such a triple, it should discard it. A node must never put its own nodeID into a bucket as a contact."
			 */
			val contactsSet = (newUnasked diff (awaiting union failed union active)).take(1)
			val newAwaiting = ((awaiting - from) union contactsSet)
			//What happens when newAwaiting is empty? we should terminate and answer our master
			
			if(newActive.size == KadAct.k || newAwaiting.isEmpty) {
				master ! LookupValueResponse(generation, key, Right(newActive))
				goto(Idle) using NullData
			} else {
				broadcastFindValue(contactsSet, generation, key)
				
				stay using currentData.copy(unasked = newUnasked, awaiting = newAwaiting, active = newActive)
			}
		}
		case Event(FindValueResponse(from, generation, lv @ Left(value)), currentData @ Data(Some(key), dataGeneration, unasked, awaiting, failed, active)) if dataGeneration == generation && awaiting.contains(from) => {
			//Found the value in the network
			cancelTimer(from.nodeID.toString())

			routingTable ! Insert(from)
		
			master ! LookupValueResponse(generation, key, lv)
			goto(Idle) using NullData
		}
		
		case Event(Timeout(contact), currentData @ Data(Some(key), dataGeneration, unasked, awaiting, failed, active)) if awaiting.contains(contact) => {
			val contactsSet = (unasked).take(1)

			if(contactsSet.isEmpty && awaiting.size == 1){
				//there's no one else to contact and we were the only ones left
				master ! LookupValueResponse(dataGeneration, key, Right(active))
				goto(Idle) using NullData
			} else {
				//here, contactsSet may be empty either, but this code will only result in emptying the awaiting set and filling up the failed. Must love higher order ops :)
				val newFailed = (failed + contact)
				val newUnasked = unasked diff contactsSet
				val newAwaiting = (awaiting - contact) union contactsSet
				
				broadcastFindValue(contactsSet, dataGeneration, key)
				
				stay using currentData.copy(unasked = newUnasked, awaiting = newAwaiting, failed = newFailed)
			}
		}
	}
	*/
	initialize
}
