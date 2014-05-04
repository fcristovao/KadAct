package kadact.node

import akka.actor._
import scala.collection.immutable.{TreeSet, HashMap}
import scala.util.Random
import scala.math.BigInt
import kadact.node.routing.RoutingTableProducer
import kadact.config.KadActConfig
import scala.Some
import scaldi.Injector
import kadact.node.lookup.LookupManagerProducer
import kadact.node.lookup.LookupManager.{LookupNodeResponse, LookupNode}

object KadActNode {
  val SHA1Hasher = java.security.MessageDigest.getInstance("SHA-1")
  private val random = new Random()

  sealed trait State
  case object Uninitialized extends State
  case object Joining extends State
  case object AwaitingRoutingTableResponse extends State
  case object Initializing extends State
  case object Working extends State

  sealed trait Data
  case object Nothing extends Data
  case class InitializingData(awaitingResponse: ActorRef, awaitingRemainingMessages: Int) extends Data
  case class StoredValues[V](values: Map[Key, V]) extends Data

  sealed trait Messages
  sealed trait InterfaceMessages extends Messages

  case object Start extends InterfaceMessages
  case class Join(contact: Contact) extends InterfaceMessages
  case object GetContact extends InterfaceMessages
  case class AddToNetwork[V](key: Key, value: V) extends InterfaceMessages
  case class GetFromNetwork[V](key: Key) extends InterfaceMessages
  case object Done extends InterfaceMessages
  case class Error(reason: ErrorReason) extends InterfaceMessages

  sealed trait ErrorReason
  case class InvalidKey(key: Key) extends ErrorReason

  def generateNewNodeID(implicit config: KadActConfig): NodeID = {
    BigInt(config.B, random)
  }

  def isValidId(id: GenericID)(implicit config: KadActConfig): Boolean = {
    id >= BigInt(0) && id < BigInt(2).pow(config.B)
  }
}

class KadActNode[V](val nodeID: NodeID)(implicit config: KadActConfig, injector: Injector)
  extends Actor
          with FSM[KadActNode.State, KadActNode.Data]
          with LoggingFSM[KadActNode.State, KadActNode.Data] {
  import KadActNode._

  import routing.RoutingTable._
  import context._
  import kadact.messages._

  require(isValidId(nodeID), "The provided nodeId (" + nodeID + ") is not valid.")

  val selfContact: Contact = Contact(nodeID, self)
  val generationIterator = Iterator from 0
  val routingTable = actorOf(Props(classOf[RoutingTableProducer], nodeID, config, injector), "RoutingTable")
  val lookupManager = actorOf(
    Props(classOf[LookupManagerProducer[V]], selfContact, routingTable, config, injector),
    "LookupManager"
  )

  def this()(implicit config: KadActConfig, injector: Injector) = this(KadActNode.generateNewNodeID)

  def pickNNodesCloseTo(nodeID: NodeID) = {
    import akka.pattern.ask
    import scala.concurrent.Await
    import scala.concurrent.duration._

    //The hardcoded 30 seconds is just a formality. We expect that we'll never need 30 seconds to get a response from the routing table
    val routingTableNodes = Await.result(
      (routingTable ? PickNNodesCloseTo(config.k, nodeID))(30 seconds).mapTo[Set[Contact]], Duration.Inf
    )
    var result = new TreeSet[Contact]()(kadact.node.ContactClosestToOrdering(nodeID))
    result ++= routingTableNodes
    result += selfContact
    result.take(config.k)
  }

  startWith(Uninitialized, Nothing) //StoredValues[V](new HashMap[Key, V]()))

  when(Uninitialized) {
    case Event(Start, _) => {
      log.info("Started KadAct Node with ID: " + nodeID)
      goto(KadActNode.Working) replying Done using StoredValues[V](new HashMap())
    }

    case Event(Join(contact), _) => {
      log.info("Started KadAct Node with ID: " + nodeID)

      routingTable ! Insert(contact)
      lookupManager ! LookupNode(nodeID)

      goto(Joining) using InitializingData(sender, 0)
    }
  }

  when(Joining) {
    case Event(LookupNodeResponse(nodeID, _), data) if this.nodeID == nodeID => {
      routingTable ! SelectRandomIDs
      goto(AwaitingRoutingTableResponse) using data
    }
  }

  when(AwaitingRoutingTableResponse) {
    case Event(setOfNodeIDs: Set[NodeID], InitializingData(awaitingResponse, _)) => {
      log.debug("Random IDs: " + setOfNodeIDs)

      for (nodeID <- setOfNodeIDs) {
        lookupManager ! LookupNode(nodeID)
      }

      goto(Initializing) using InitializingData(awaitingResponse, setOfNodeIDs.size)
    }
  }

  when(Initializing) {
    case Event(LookupNodeResponse(_, _), InitializingData(awaitingResponse, awaitingRemainingMessages)) => {
      if (awaitingRemainingMessages > 1) {
        stay using InitializingData(awaitingResponse, awaitingRemainingMessages - 1)
      }
      else {
        awaitingResponse ! Done
      }
      goto(KadActNode.Working) using StoredValues[V](new HashMap())
    }

    case Event(FindNode(fromContact, generation, nodeID), _) => {
      routingTable ! Insert(fromContact)
      val contactsSet = pickNNodesCloseTo(nodeID)
      log.debug(FindNodeResponse(this.selfContact, generation, contactsSet).toString())

      stay replying FindNodeResponse(this.selfContact, generation, contactsSet)
    }
  }

  when(Working) {
    // Protocol Messages:
    case Event(kadact.messages.FindNode(fromContact, generation, nodeID), _) => {
      routingTable ! Insert(fromContact)

      val contactsSet = pickNNodesCloseTo(nodeID) - fromContact
      // ^-- Remove 'fromContact' because "[KademliaSpec] The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor."
      log.debug(FindNodeResponse(selfContact, generation, contactsSet).toString())

      stay replying FindNodeResponse(selfContact, generation, contactsSet)
    }

    case Event(kadact.messages.Store(fromContact, generation, key, value: V), StoredValues(storedValues: HashMap[Key, V])) => {
      routingTable ! Insert(fromContact)
      stay using (StoredValues[V](storedValues + (key -> value))) replying StoreResponse(selfContact, generation)
    }

    case Event(kadact.messages.FindValue(fromContact, generation, key), StoredValues(storedValues)) => {
      routingTable ! Insert(fromContact)

      storedValues.get(key) match {
        case None => {
          val contactsSet = pickNNodesCloseTo(nodeID) - fromContact
          // ^-- Remove 'fromContact' because "[KademliaSpec] The recipient of a FIND_NODE should never return a triple containing the nodeID of the requestor"
          log.debug(FindValueResponse(this.selfContact, generation, Right(contactsSet)).toString())
          stay replying FindValueResponse(this.selfContact, generation, Right(contactsSet))
        }
        case Some(value) => {
          log.debug(FindValueResponse(this.selfContact, generation, Left(value)).toString())
          stay replying FindValueResponse(this.selfContact, generation, Left(value))
        }
      }
    }

    case Event(kadact.messages.Ping(fromContact, generation), _) => {
      stay replying kadact.messages.Pong(selfContact, generation)
    }

    // Interface messages:
    case Event(msg@AddToNetwork(key, value), _) if isValidId(key) => {
      val nextGen = generationIterator.next()
      val tmp = actorOf(Props(new AddToNetworkActor(selfContact, nextGen, routingTable, lookupManager)), "AddToNetworkActor" + nextGen + "")
      tmp.forward(msg)
      stay()
    }

    case Event(msg@AddToNetwork(key, value), _) if !isValidId(key) => {
      stay replying Error(InvalidKey(key))
    }

    case Event(GetFromNetwork(key), StoredValues(storedValues: HashMap[Key, V])) => {
      stay replying storedValues.get(key)
    }
  }

  whenUnhandled {
    case Event(GetContact, _) =>
      stay replying selfContact
  }

  initialize()

  override def toString: String = {
    "KadActNode(" + nodeID + ")"
  }
}
