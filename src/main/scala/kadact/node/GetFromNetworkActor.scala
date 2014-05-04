package kadact.node

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM}
import kadact.config.KadActConfig


object GetFromNetworkActor {
  sealed trait State
  case object Idle extends State
  case object Working extends State

  sealed trait Data
  case class Working[V](replyTo: ActorRef) extends Data
  case object NullData extends Data
}

class GetFromNetworkActor[V](lookupManager: ActorRef)(implicit config: KadActConfig)
  extends Actor
          with FSM[GetFromNetworkActor.State, GetFromNetworkActor.Data]
          with LoggingFSM[GetFromNetworkActor.State, GetFromNetworkActor.Data] {
  import GetFromNetworkActor._
  import lookup.LookupManager

  startWith(Idle, NullData)

  when(Idle) {
    case Event(KadActNode.GetFromNetwork(key), _) => {
      lookupManager ! LookupManager.LookupValue(key)
      goto(Working) using Working[V](sender())
    }
  }

  when(Working) {
    case Event(LookupManager.LookupValueResponse(_, Left(value: V)), Working(replyTo)) => {
      replyTo ! Some(value)
      stop()
    }
    case Event(LookupManager.LookupValueResponse(_, Right(_)), Working(replyTo)) => {
      replyTo ! None
      stop()
    }
  }
}
