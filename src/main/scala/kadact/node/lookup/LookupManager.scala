package kadact.node.lookup

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM, ActorLogging, Props}
import akka.actor.Actor._
import akka.event.{Logging, LoggingReceive}

import kadact.KadAct
import kadact.node._
import scala.collection.immutable.Queue

//There should be a NodeLookupManager to deal with the creation of NodeLookups

object LookupManager {
	//import NodeLookup.{Lookup, LookupResponse}
	
	private[LookupManager] sealed trait State
	private[LookupManager] case object Working extends State
	private[LookupManager] case object Full extends State
	
	sealed trait LookupType
	case object Node extends LookupType
	case object Value extends LookupType
	
	sealed trait Messages
	case class LookupNode(nodeID: NodeID) extends Messages
	case class LookupNodeResponse(nodeID: NodeID, contacts: Set[Contact]) extends Messages
	case class LookupValue(key: Key) extends Messages
	case class LookupValueResponse[V](key: Key, answer: Either[V, Set[Contact]]) extends Messages
	
	object Lookup {
		def unapply(msg: Messages) : Option[(LookupType, GenericID)] = {
			msg match {
				case LookupNode(nodeID) => Some(Node, nodeID)
				case LookupValue(key) => Some(Value, key)
				case _ => None
			}
		}
	}
	
	case class Data(idleList: List[ActorRef] = Nil, workingList: List[ActorRef] = Nil, awaitingActors: Map[Int, ActorRef] = Map(), pendingLookups: Queue[(Messages, ActorRef)] = Queue())
}

class LookupManager[V](originalNode: Contact, routingTable: ActorRef) extends Actor with FSM[LookupManager.State, LookupManager.Data] with LoggingFSM[LookupManager.State, LookupManager.Data]{
	import FSM._
	import LookupManager._
	//import NodeLookup.{Lookup, LookupResponse}
	
	val generationIterator = Iterator from 0
	
	startWith(Working, Data())
	
	when(Working){
		//There are idle LookupNodes available to take the request
		case Event(Lookup(lookupType, id), currentData @ Data(someWorker :: tail, workList, awaitingActors, _)) => {
			val nextGen = generationIterator.next()
			
			lookupType match {
				case Node => someWorker ! NodeLookup.LookupNode(nextGen, id)
				case Value => //insert here the code for the value lookup
			}
			
			stay using currentData.copy(idleList = tail, workingList = someWorker :: workList, awaitingActors = awaitingActors + (nextGen -> sender))
		}
		
		//No more idle LookupNodes exist, but we are yet allowed to create more to process this request
		case Event(lookupMsg @ Lookup(_,_), currentData @ Data(Nil, workList, _,_)) if workList.size < KadAct.maxParallelLookups => {
			//We just create a new actor and resend the message to be reprocessed. This is to avoid duplicate code, although penalizes performance
			val someWorker = context.actorOf(Props(new LookupSplitter(this.self, originalNode, routingTable)))
			//We can't use ! because it would make us the sender, and we don't want that.
			self forward lookupMsg
			
			stay using currentData.copy(List(someWorker))
		}
		
		//No idle LookupNodes exist, and we are no longer allowed to create another
		case Event(lookupMsg @ Lookup(_,_), currentData @ Data(Nil, workList, awaitingActors, pendingLookups)) if workList.size == KadAct.maxParallelLookups => {
			goto(Full) using currentData.copy(pendingLookups = pendingLookups.enqueue((lookupMsg, sender)))
		}
		
		//We get a response from one of our minions :)
		case Event(NodeLookup.LookupNodeResponse(generation, nodeID, contacts), currentData @ Data(idleList, workingList, awaitingActors, _)) => {
			awaitingActors(generation) ! LookupNodeResponse(nodeID, contacts)
			
			stay using currentData.copy(idleList = sender :: idleList, workingList = workingList filterNot(_ == sender), awaitingActors = awaitingActors - generation)
		}
		
	}
	
	when(Full) {
		//We're already full, so just save it for later
		case Event(lookupMsg: Messages, currentData @ Data(_, _, _, pendingLookups)) => {
			stay using currentData.copy(pendingLookups = pendingLookups.enqueue((lookupMsg, sender)))
		}
		
		//When we get a response, just use that minion to make other lookup and carry on
		case Event(NodeLookup.LookupNodeResponse(generation, nodeID, contacts), currentData @ Data(_, _, awaitingActors, pendingLookups)) => {
			awaitingActors(generation) ! LookupNodeResponse(nodeID, contacts)
			
			val ((Lookup(lookupType, id), newAwaitingActor), newQueue) = pendingLookups.dequeue
			val someWorker = sender
			val nextGen = generationIterator.next()
			
			lookupType match {
				case Node => someWorker ! NodeLookup.LookupNode(nextGen, id)
				case Value => //insert here the code for the value lookup
			}

			val newAwaitingActors = awaitingActors - generation + (nextGen -> newAwaitingActor)
			
			val newState = 
				if(pendingLookups.size == 1){
					goto(Working)
				} else {
					stay
				}
			newState using currentData.copy(awaitingActors = newAwaitingActors, pendingLookups = newQueue)
		}
	}
	
	initialize
}