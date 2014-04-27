package kadact.config.modules

import scaldi.Module
import kadact.node._
import kadact.node.routing.{RoutingTableFactory, RoutingTable}
import kadact.config.KadActConfig

class RoutingTableModule extends Module {
  private val factory = new RoutingTableFactory {
    override def build(origin: NodeID)(implicit config: KadActConfig): RoutingTable = new RoutingTable(origin)
  }
  bind[RoutingTableFactory] to factory
}
