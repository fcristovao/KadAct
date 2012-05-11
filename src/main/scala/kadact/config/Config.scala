package kadact.config
import com.typesafe.config.ConfigFactory

import collection.JavaConversions._

object Config {
	val config = ConfigFactory.parseMap(Map("kadact.alpha" -> 3, "kadact.B" -> 4, "kadact.k" -> 2, "kadact.s" -> 2, "kadact.timeouts.nodeLookup" -> 5))
}