package kadact.node

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM}
import kadact.config.KadActConfig


object AddToNetworkActor {
  sealed trait State
  case object Start extends State
  case object Working extends State
  case object CheckResults extends State
  case object Answer extends State

  sealed trait Messages
  private case class Timeout(contact: Contact) extends Messages

  case class Data[V](replyTo: Option[ActorRef] = None,
                     pair: Option[(Key, V)] = None,
                     awaiting: Set[Contact] = Set(),
                     answered: Set[Contact] = Set(),
                     failed: Set[Contact] = Set())
}

class AddToNetworkActor[V](originalNode: Contact, generation: Int, routingTable: ActorRef, lookupManager: ActorRef)(implicit config: KadActConfig)
  extends Actor
          with FSM[AddToNetworkActor.State, AddToNetworkActor.Data[V]]
          with LoggingFSM[AddToNetworkActor.State, AddToNetworkActor.Data[V]] {
  import AddToNetworkActor._
  import lookup.LookupManager
  //import NodeFSM._
  import kadact.messages._

  startWith(Start, Data[V]())

  when(Start) {
    case Event(KadActNode.AddToNetwork(key, value: V), _) => {
      lookupManager ! LookupManager.LookupNode(key)
      goto(Working) using Data(Some(sender), Some((key, value)))
    }
  }

  when(Working) {
    case Event(LookupManager.LookupNodeResponse(nodeID, contacts), currentData@Data(_, Some((key, value)), awaiting, answered, failed)) => {
      val alreadyContacted = awaiting ++ answered ++ failed
      val nodesToContact = contacts -- alreadyContacted

      if (nodesToContact.isEmpty) {
        goto(Answer)
      } else {
        nodesToContact foreach {
          contact =>
            setTimer(contact.nodeID.toString(), Timeout(contact), config.Timeouts.storeValue)
            contact.node ! Store(originalNode, generation, key, value)
        }
        stay using currentData.copy(awaiting = awaiting ++ nodesToContact)
      }
    }
    case Event(StoreResponse(contact, generation), currentData@Data(_, _, awaiting, answered, _))
      if generation == this.generation /* && awaiting.contains(contact) */ => {
      val newAwaiting = awaiting - contact
      val newAnswered = answered + contact

      if (newAwaiting.isEmpty) {
        goto(CheckResults)
      } else {
        stay using currentData.copy(awaiting = newAwaiting, answered = newAnswered)
      }
    }
    case Event(Timeout(contact), currentData@Data(_, Some((key, value)), awaiting, _, failed)) => {
      val newAwaiting = awaiting - contact
      val newFailed = failed + contact

      val newState =
        if (newAwaiting.isEmpty) {
          goto(CheckResults)
        } else {
          stay
        }

      newState using currentData.copy(awaiting = newAwaiting, failed = newFailed)
    }
  }

  onTransition {
    case _ -> CheckResults => self !()
    case _ -> Answer => self !()
  }

  when(CheckResults) {
    case Event((), currentData@Data(Some(sender), Some((key, _)), _, _, failed)) => {
      if (failed.isEmpty) {
        goto(Answer)
      } else {
        lookupManager ! LookupManager.LookupNode(key)
        goto(Working)
      }
    }
  }

  when(Answer) {
    case Event((), currentData@Data(Some(sender), _, _, _, _)) => {
      sender ! KadActNode.Done
      stop
    }
  }

}
