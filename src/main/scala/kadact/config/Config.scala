package kadact.config
import akka.config.Configuration

object Config {
	val config = Configuration.fromMap(Map("kadact.alpha" -> 3, "kadact.B" -> 4, "kadact.k" -> 2, "kadact.timeouts.nodeLookup" -> 5))
}