package kadact.config

import com.typesafe.config.ConfigFactory

object TestKadActConfig {
  import scala.collection.JavaConversions._

  def apply() = new KadActConfig(ConfigFactory.parseMap(Map("kadact.alpha" -> "2",
                                                            "kadact.B" -> "4",
                                                            "kadact.k" -> "2",
                                                            "kadact.s" -> "1",
                                                            "kadact.timeouts.nodeLookup" -> "5"
                                                           )))
}