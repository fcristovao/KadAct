package kadact.node.lookup

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM, ActorLogging, Props}
import akka.actor.Actor._
import kadact.node._
import kadact.config.KadActConfig

object LookupSplitter {
	sealed trait State
	case object Idle extends State
	case object NodeLookupState extends State
	case object ValueLookupState extends State
}


class LookupSplitter(master: ActorRef, originalNode: Contact, routingTable: ActorRef)(implicit config: KadActConfig) extends Actor with FSM[LookupSplitter.State, Unit] with LoggingFSM[LookupSplitter.State, Unit]{
	import LookupSplitter._
	
	val nodeLookup = context.actorOf(Props(new NodeLookup(this.self, originalNode, routingTable)),"NodeLookup")
	val valueLookup = context.actorOf(Props(new ValueLookup(this.self, originalNode, routingTable)),"ValueLookup")
	
	startWith(Idle, ())
	
	when(Idle){
		case Event(msg: NodeLookup.LookupNode,_) => {
			nodeLookup ! msg
			
			goto(NodeLookupState)
		}
		case Event(msg: ValueLookup.LookupValue,_) => {
			valueLookup ! msg
			
			goto(ValueLookupState)
		}
	}
	
	when(NodeLookupState){
		case Event(msg: NodeLookup.LookupNodeResponse, _) => {
			master ! msg
			
			goto(Idle)
		}
	}
	
	when(ValueLookupState){
		case Event(msg: ValueLookup.LookupValueResponse[_], _) => {
			master ! msg
			
			goto(Idle)
		}
	}

	initialize
}