package kadact.config.modules

import scaldi.Module
import kadact.node.Contact
import kadact.node.routing.{RoutingTableFactory, RoutingTable}
import kadact.config.KadActConfig
import kadact.node.lookup.{LookupManager, LookupManagerFactory}
import akka.actor.ActorRef

class LookupManagerModule extends Module {
  private val factory = new LookupManagerFactory {
    override def build[V](originalNode: Contact, routingTable: ActorRef)(implicit config: KadActConfig): LookupManager[V] =
      new LookupManager[V](originalNode, routingTable)
  }
  bind [LookupManagerFactory] to factory
 }
