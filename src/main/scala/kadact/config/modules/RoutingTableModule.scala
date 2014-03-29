package kadact.config.modules

import scaldi.Module
import kadact.node.Contact
import kadact.node.routing.{RoutingTableFactory, RoutingTable}
import kadact.config.KadActConfig

class RoutingTableModule extends Module {
  private val factory = new RoutingTableFactory {
    override def build(originalNode: Contact)(implicit config: KadActConfig): RoutingTable = new RoutingTable(originalNode)
  }
  bind [RoutingTableFactory] to factory
}
