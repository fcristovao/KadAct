package kadact.node.lookup

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM}
import kadact.node._
import kadact.config.KadActConfig
import scala.collection.immutable.TreeSet

object NodeLookup {
  sealed trait State
  case object Idle extends State
  case object AwaitingContactsSet extends State
  case object AwaitingResponses extends State
  case object Answer extends State

  sealed trait Messages
  private case class Timeout(contact: Contact) extends Messages
  case class LookupNode(generation: Int, nodeID: NodeID) extends Messages
  case class LookupNodeResponse(generation: Int, nodeID: NodeID, contacts: Set[Contact]) extends Messages

  sealed trait Data
  case class Working(request: (ActorRef, LookupNode),
                     active: Set[Contact],
                     unasked: Set[Contact],
                     waiting: Set[Contact] = Set(),
                     failed: Set[Contact] = Set()) extends Data
  case object NullData extends Data

}

class NodeLookup(originalNode: Contact, routingTable: ActorRef)(implicit config: KadActConfig)
  extends Actor
          with FSM[NodeLookup.State, NodeLookup.Data]
          with LoggingFSM[NodeLookup.State, NodeLookup.Data] {
  
  import NodeLookup._
  import routing.RoutingTable._
  import kadact.messages._

  def broadcastFindNode(contacts: Set[Contact], generation: Int, nodeID: NodeID) {
    for (contact <- contacts) {
      setTimer(contact.nodeID.toString(), Timeout(contact), config.Timeouts.nodeLookup)
      contact.node ! FindNode(originalNode, generation, nodeID)
    }
  }

  def cancelRemainingTimers(awaitingForContacts: Set[Contact]) {
    for (contact <- awaitingForContacts) {
      cancelTimer(contact.nodeID.toString())
    }
  }

  startWith(Idle, NullData)

  when(Idle) {
    case Event(msg@LookupNode(_, nodeID), _) =>
      routingTable ! PickNNodesCloseTo(config.alpha, nodeID)
      val ordering = ContactClosestToOrdering(nodeID)
      // Our own node is always considered as active and always the closest until one better is found
      val activeSet = new TreeSet[Contact]()(ordering) + originalNode
      val unaskedSet = new TreeSet[Contact]()(ordering)
      goto(AwaitingContactsSet) using Working(
        request = sender -> msg,
        active = activeSet,
        unasked = unaskedSet
      )
  }

  when(AwaitingContactsSet) {
    case Event(contactsSet: Set[Contact], currentData@Working((_, LookupNode(generation, nodeID)), _, _, _, _)) => {
      if (contactsSet.isEmpty) {
        goto(Answer)
      } else {
        broadcastFindNode(contactsSet, generation, nodeID)
        goto(AwaitingResponses) using currentData.copy(waiting = contactsSet)
      }
    }
  }

  when(AwaitingResponses) {
    case Event(FindNodeResponse(from, generation, contacts), currentData@Working((_, LookupNode(lookupGeneration, nodeID)), active, unasked, awaiting, failed))
      if lookupGeneration == generation && awaiting.contains(from) => {
      cancelTimer(from.nodeID.toString())

      routingTable ! Insert(from)

      val newActive = active + from
      val alreadyContacted = newActive union awaiting union failed
      val newUnasked = ((unasked union contacts) -- alreadyContacted) - originalNode
      /* ^-- Drop 'originalNode' because:
       * "[KademliaSpec] The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor.
       * If the requestor does receive such a triple, it should discard it."
       */
      val contactsSet = newUnasked.take(1)
      val newAwaiting = (awaiting - from) union contactsSet

      val newData = currentData.copy(unasked = newUnasked, waiting = newAwaiting, active = newActive)

      // + 1 because we start with the originalNode, but want to find config.k more
      if (newActive.size == config.k + 1 || newAwaiting.isEmpty) {
        // We've reached an answer, but we might already have sent requests to other nodes, that are now pending.
        cancelRemainingTimers(newAwaiting)
        goto(Answer) using newData
      } else {
        broadcastFindNode(contactsSet, generation, nodeID)
        stay using newData
      }
    }
    case Event(Timeout(contact), currentData@Working((answerTo, LookupNode(generation, nodeID)), active, unasked, awaiting, failed))
      if awaiting.contains(contact) => {
      //contactsSet may be empty, but this code will only result in emptying the awaiting set and filling up the failed.
      val contactsSet = unasked.take(1)
      val newAwaiting = (awaiting - contact) union contactsSet
      val newData = currentData.copy(
        unasked = unasked -- contactsSet,
        waiting = newAwaiting,
        failed = failed + contact
      )

      if (newAwaiting.size == 0) {
        //there's no one else to contact
        goto(Answer) using newData
      } else {
        broadcastFindNode(contactsSet, generation, nodeID)
        stay using newData
      }
    }
  }

  when(Answer) {
    case Event((), Working((answerTo, LookupNode(generation, nodeID)), active, _, _, _)) => {
      answerTo ! LookupNodeResponse(generation, nodeID, active.take(config.k))
      goto(Idle) using NullData
    }
  }

  onTransition {
    case _ -> Answer => self !()
  }

  initialize()
}
