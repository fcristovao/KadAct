package kadact.node.lookup

import akka.actor._
import kadact.node._
import scala.collection.immutable.Queue
import kadact.config.KadActConfig
import scala.Some
import kadact.node.Contact
import scaldi.{Injector, Injectable}

object LookupManager {
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
    def unapply(msg: Messages): Option[(LookupType, GenericID)] = {
      msg match {
        case LookupNode(nodeID) => Some(Node, nodeID)
        case LookupValue(key) => Some(Value, key)
        case _ => None
      }
    }
  }

  case class Data(workingActors: List[ActorRef] = Nil,
                  pendingLookups: Queue[(Messages, ActorRef)] = Queue())
}

trait LookupManagerFactory {
  def build[V](originalNode: Contact, routingTable: ActorRef)(implicit config: KadActConfig): LookupManager[V]
}

class LookupManagerProducer[V](originalNode: Contact, routingTable: ActorRef)(implicit config: KadActConfig, injector: Injector)
  extends IndirectActorProducer with Injectable {
  override def actorClass = classOf[LookupManager[V]]

  override def produce = inject[LookupManagerFactory].build(originalNode, routingTable)
}

class LookupManager[V](originalNode: Contact, routingTable: ActorRef)(implicit config: KadActConfig)
  extends Actor
          with FSM[LookupManager.State, LookupManager.Data]
          with LoggingFSM[LookupManager.State, LookupManager.Data] {
  import LookupManager._

  val generationIterator = Iterator from 0

  startWith(Working, Data())

  when(Working) {
    case Event(Lookup(lookupType, id), currentData@Data(workingActors, _)) => {
      val nextGen = generationIterator.next()

      val workerActor = lookupType match {
        case Node => {
          val nodeLookupActor = context.actorOf(
            Props(classOf[NodeLookup], originalNode, routingTable, nextGen, config),
            "NodeLookup" + nextGen
          )
          nodeLookupActor forward LookupNode(id)
          nodeLookupActor
        }
        case Value => {
          val valueLookupActor = context.actorOf(
            Props(classOf[ValueLookup[V]], originalNode, routingTable, nextGen, config),
            "ValueLookup" + nextGen
          )
          valueLookupActor forward LookupNode(id)
          valueLookupActor
        }
      }

      context.watch(workerActor)
      val newWorkingActors = workerActor :: workingActors
      if (newWorkingActors.size >= config.maxParallelLookups) {
        goto(Full) using currentData.copy(workingActors = newWorkingActors)
      } else {
        stay using currentData.copy(workingActors = newWorkingActors)
      }
    }
  }

  when(Full) {
    case Event(lookupMsg@Lookup(_, _), currentData@Data(_, pendingLookups)) => {
      stay using currentData.copy(pendingLookups = pendingLookups.enqueue((lookupMsg, sender)))
    }
  }

  whenUnhandled {
    // One of our minions has finished its work:
    case Event(Terminated(workerActor), currentData@Data(workingActors, pendingLookups)) => {
      val newWorkingActors = workingActors filter (_ != workerActor)
      if (newWorkingActors.size >= config.maxParallelLookups) {
        goto(Full) using currentData.copy(workingActors = newWorkingActors)
      } else {
        val newPendingLookups =
          if (pendingLookups.isEmpty) {
            pendingLookups
          } else {
            val ((lookupMsg, ancientSender), newQueue) = pendingLookups.dequeue
            self.tell(lookupMsg, ancientSender)
            newQueue
          }
        goto(Working) using currentData.copy(
          workingActors = newWorkingActors,
          pendingLookups = newPendingLookups
        )
      }
    }
  }

  initialize()
}