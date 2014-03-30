package kadact.messages

import kadact.node.{NodeID, Key, Contact}

sealed class ProtocolMessages(from: Contact, generation: Int) extends java.io.Serializable

case class FindNode(from: Contact, generation: Int, nodeID: NodeID) extends ProtocolMessages(from, generation)
case class FindNodeResponse(from: Contact, generation: Int, contacts: Set[Contact]) extends ProtocolMessages(from, generation)

case class FindValue(from: Contact, generation: Int, key: Key) extends ProtocolMessages(from, generation)
case class FindValueResponse[V](from: Contact, generation: Int, answer: Either[V, Set[Contact]]) extends ProtocolMessages(from, generation)

case class Ping(from: Contact, generation: Int) extends ProtocolMessages(from, generation)
case class Pong(from: Contact, generation: Int) extends ProtocolMessages(from, generation)

case class Store[V](from: Contact, generation: Int, key: Key, value: V) extends ProtocolMessages(from, generation)
case class StoreResponse(from: Contact, generation: Int) extends ProtocolMessages(from, generation)