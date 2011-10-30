package kadact.node

import akka.actor.ActorRef

case class Contact(nodeID: NodeID, node: ActorRef)