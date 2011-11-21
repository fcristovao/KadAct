package kadact.node

import akka.actor.{Actor, ActorRef, FSM}
import akka.actor.Actor._
import akka.util.Duration
import kadact.KadAct
import akka.actor.LoggingFSM
import scala.collection.immutable.Queue

//There should be a NodeLookupManager to deal with the creation of NodeLookups

object NodeLookupManager {
	import NodeLookup.{Lookup, LookupResponse}
	
	sealed trait State
	case object Working extends State
	case object Full extends State
	
	case class Data(idleList: List[ActorRef] = Nil, workingList: List[ActorRef] = Nil, pendingLookups: Queue[Lookup] = Queue())
}

class NodeLookupManager(master: ActorRef, originalNode: Contact, routingTable: ActorRef) extends Actor with FSM[NodeLookupManager.State, NodeLookupManager.Data] with LoggingFSM[NodeLookupManager.State, NodeLookupManager.Data]{
	import FSM._
	import NodeLookupManager._
	import NodeLookup.{Lookup, LookupResponse}
	
	startWith(Working, Data(idleList = actorOf(new NodeLookup(this.self, originalNode, routingTable)).start() :: Nil))
	
	when(Working){
		case Event(look @ Lookup(_), currentData @ Data(someWorker :: tail, workList, _)) => {
			someWorker ! look
			
			stay using currentData.copy(idleList = tail, workingList = someWorker :: workList)
		}
		
		case Event(look @ Lookup(_), currentData @ Data(Nil, workList, _)) if workList.size < KadAct.maxParallelLookups => {
			val nodeLookup = actorOf(new NodeLookup(this.self, originalNode, routingTable)).start()
			nodeLookup ! look

			stay using currentData.copy(workingList = nodeLookup :: workList)
		}
		
		case Event(look @ Lookup(_), currentData @ Data(Nil, workList, pendingLookups)) if workList.size == KadAct.maxParallelLookups => {
			goto(Full) using currentData.copy(pendingLookups = pendingLookups.enqueue(look))
		}
		
		case Event(lookResponse @ LookupResponse(_,_), currentData @ Data(idleList, workingList, _)) => {
			val worker = self.sender.get
			master ! lookResponse
			stay using currentData.copy(idleList = worker :: idleList, workingList = workingList filterNot(_ == worker))
		}
		
	}
	
	when(Full) {
		case Event(look @ Lookup(_), currentData @ Data(_, _, pendingLookups)) => {
			stay using currentData.copy(pendingLookups = pendingLookups.enqueue(look))
		}
		
		case Event(lookResponse @ LookupResponse(_,_), currentData @ Data(_, _, pendingLookups)) if pendingLookups.size > 1 => {
			master ! lookResponse
			val (look, newQueue) = pendingLookups.dequeue
			self.reply(look) // just tell to the NodeLookup Actor that just sent us the response to process another Lookup message 
			stay using currentData.copy(pendingLookups = newQueue)
		}
		
		case Event(lookResponse @ LookupResponse(_,_), currentData @ Data(_, _, pendingLookups)) if pendingLookups.size == 1 => {
			master ! lookResponse
			val (look, newQueue) = pendingLookups.dequeue
			self.reply(look) // just tell to the NodeLookup Actor that just sent us the response to process another Lookup message 
			goto(Working) using currentData.copy(pendingLookups = newQueue)
		}
	}
	
	initialize
}