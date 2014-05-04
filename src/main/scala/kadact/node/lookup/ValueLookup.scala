package kadact.node.lookup

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM}
import kadact.node._
import kadact.config.KadActConfig
import scala.collection.immutable.TreeSet
import kadact.node.lookup.LookupManager.{LookupValueResponse, LookupValue}

object ValueLookup {
  sealed trait State
  case object Idle extends State
  case object AwaitingContactsSet extends State
  case object AwaitingResponses extends State
  case object Answer extends State
  case object StoringValueInClosestNode extends State

  sealed trait Messages
  private case class Timeout(contact: Contact) extends Messages

  sealed trait Data
  case class Working[V](request: (ActorRef, LookupValue),
                        active: Set[Contact],
                        unasked: Set[Contact],
                        waiting: Set[Contact] = Set(),
                        failed: Set[Contact] = Set(),
                        value: Option[V] = None) extends Data
  case class StoringValueInClosestNode[V](active: Set[Contact], waiting: Contact, keyValue: (Key, V)) extends Data
  case object NullData extends Data
}

class ValueLookup[V](originalNode: Contact, routingTable: ActorRef, generation: Int)(implicit config: KadActConfig)
  extends Actor
          with FSM[ValueLookup.State, ValueLookup.Data]
          with LoggingFSM[ValueLookup.State, ValueLookup.Data] {

  import ValueLookup._
  import routing.RoutingTable._
  import kadact.messages._

  def broadcastFindValue(contacts: Set[Contact], key: Key) {
    for (contact <- contacts) {
      setTimer(contact.nodeID.toString(), Timeout(contact), config.Timeouts.nodeLookup)
      contact.node ! FindValue(originalNode, generation, key)
    }
  }

  def sendStoreValue(contact: Contact, key: Key, value: V) {
      setTimer(contact.nodeID.toString(), Timeout(contact), config.Timeouts.nodeLookup)
      contact.node ! Store(originalNode, generation, key, value)
  }

  def cancelRemainingTimers(awaitingForContacts: Set[Contact]) {
    for (contact <- awaitingForContacts) {
      cancelTimer(contact.nodeID.toString())
    }
  }

  startWith(Idle, NullData)

  when(Idle) {
    case Event(msg@LookupValue(key), _) =>
      routingTable ! PickNNodesCloseTo(config.alpha, key)
      val ordering = ContactClosestToOrdering(key)
      // Our own node is always considered as active and always the closest until one better is found
      val activeSet = new TreeSet[Contact]()(ordering) + originalNode
      val unaskedSet = new TreeSet[Contact]()(ordering)
      goto(AwaitingContactsSet) using Working[V](
        request = sender -> msg,
        active = activeSet,
        unasked = unaskedSet
      )
  }

  when(AwaitingContactsSet) {
    case Event(contactsSet: Set[Contact], currentData@Working((_, LookupValue(key)), _, _, _, _, _)) => {
      if (contactsSet.isEmpty) {
        goto(Answer)
      } else {
        broadcastFindValue(contactsSet, key)
        goto(AwaitingResponses) using currentData.copy(waiting = contactsSet)
      }
    }
  }

  when(AwaitingResponses) {
    case Event(FindValueResponse(from, lookupGeneration, Right(contacts)), currentData@Working((_, LookupValue(key)), active, unasked, awaiting, failed, _))
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
        goto(Answer) using newData
      } else {
        broadcastFindValue(contactsSet, key)
        stay using newData
      }
    }
    case Event(FindValueResponse(from, lookupGeneration, Left(value)), currentData@Working((_, LookupValue(key)), active, unasked, awaiting, failed, _))
      if lookupGeneration == generation && awaiting.contains(from) => {
      cancelTimer(from.nodeID.toString())

      routingTable ! Insert(from)

      goto(Answer) using currentData.copy(active = active + from, value = Some(value))
    }
    case Event(Timeout(contact), currentData@Working((answerTo, LookupValue(key)), active, unasked, awaiting, failed, _))
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
        broadcastFindValue(contactsSet, key)
        stay using newData
      }
    }
  }

  when(Answer) {
    case Event((), Working((answerTo, LookupValue(key)), active, _, awaiting, _, valueOption: Option[V])) => {
      // We've reached an answer, but we might already have sent requests to other nodes, that are now pending.
      cancelRemainingTimers(awaiting)
      
      valueOption match {
        case None =>  {
          answerTo ! LookupValueResponse(key, Right(active.take(config.k)))
          stop()
        }
        case Some(value) => {
          answerTo ! LookupValueResponse(key, Left(value))
          val closestNode = active.head
          sendStoreValue(closestNode, key, value)
          goto(StoringValueInClosestNode) using StoringValueInClosestNode(active.tail, closestNode, key -> value)
        }
      }
    }
  }

  when(StoringValueInClosestNode) {
    case Event(StoreResponse(fromActor, storeGeneration), StoringValueInClosestNode(active, waitingFromActor, key -> value))
    if storeGeneration == generation && waitingFromActor == fromActor => {
      cancelTimer(fromActor.nodeID.toString())
      routingTable ! Insert(fromActor)

      stop()
    }
    case Event(Timeout(fromActor), StoringValueInClosestNode(active, waitingFromActor, (key, value: V))) => {
      if (active.isEmpty) {
        stop()
      } else {
        val closestNode = active.head
        sendStoreValue(closestNode, key, value)
        stay using StoringValueInClosestNode(active.tail, closestNode, key -> value)
      }
    }
  }

  onTransition {
    case _ -> Answer => self !()
  }

  initialize()
}
