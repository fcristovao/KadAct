package kadact.node.lookup

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM}
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

  case class Data(request: Option[(ActorRef, LookupNode)] = None,
                  unasked: Set[Contact] = Set(),
                  waiting: Set[Contact] = Set(),
                  failed: Set[Contact] = Set(),
                  active: Set[Contact] = Set())

  val NullData = Data()
}

class NodeLookup(originalNode: Contact, routingTable: ActorRef)(implicit config: KadActConfig) extends Actor
                                                                                                       with FSM[NodeLookup.State, NodeLookup.Data]
                                                                                                       with LoggingFSM[NodeLookup.State, NodeLookup.Data] {
  import NodeLookup._
  import routing.RoutingTable._
  import kadact.messages._

  def broadcastFindNode(contacts: Set[Contact], generation: Int, nodeID: NodeID) {
    for (contact <- contacts) {
      setTimer(contact.nodeID.toString(), Timeout(contact), config.Timeouts.nodeLookup, false)
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
      goto(AwaitingContactsSet) using Data(request = Some(sender -> msg))
  }

  when(AwaitingContactsSet) {
    case Event(contactsSet: Set[Contact], currentData@Data(Some((answerTo, LookupNode(generation, nodeID))), _, _, _, _)) if contactsSet
                                                                                                                             .isEmpty => {
      answerTo ! LookupNodeResponse(generation, nodeID, Set(originalNode))
      goto(Idle) using NullData
    }
    case Event(contactsSet: Set[Contact], currentData@Data(Some((_, LookupNode(generation, nodeID))), _, _, _, _)) => {
      broadcastFindNode(contactsSet, generation, nodeID)
      goto(AwaitingResponses) using currentData.copy(waiting = contactsSet)
    }
  }

  when(AwaitingResponses) {
    case Event(FindNodeResponse(from, generation, contacts), currentData@Data(Some((answerTo, LookupNode(lookupGeneration, nodeID))), unasked, awaiting, failed, active)) if lookupGeneration == generation && awaiting
                                                                                                                                                                                                               .contains(from) => {
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

      if (newActive.size == config.k || newAwaiting.isEmpty) {
        answerTo ! LookupNodeResponse(generation, nodeID, newActive)
        cancelRemainingTimers(newAwaiting) // We've reached an answer, but we might already have sent requests to other nodes, that are now pending.
        goto(Idle) using NullData
      } else {
        broadcastFindNode(contactsSet, generation, nodeID)
        stay using currentData.copy(unasked = newUnasked, waiting = newAwaiting, active = newActive)
      }
    }
    case Event(Timeout(contact), currentData@Data(Some((answerTo, LookupNode(generation, nodeID))), unasked, awaiting, failed, active)) if awaiting
                                                                                                                                           .contains(contact) => {
      val contactsSet = unasked.take(1)

      if (contactsSet.isEmpty && awaiting.size == 1) {
        //there's no one else to contact and we were the only ones left
        answerTo ! LookupNodeResponse(generation, nodeID, active)
        goto(Idle) using NullData
      } else {
        //here, contactsSet may be empty either, but this code will only result in emptying the awaiting set and filling up the failed. Must love higher order ops :)
        val newFailed = (failed + contact)
        val newUnasked = unasked -- contactsSet
        val newAwaiting = (awaiting - contact) union contactsSet

        broadcastFindNode(contactsSet, generation, nodeID)

        stay using currentData.copy(unasked = newUnasked, waiting = newAwaiting, failed = newFailed)
      }
    }
  }

  initialize()
}
