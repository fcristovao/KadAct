package kadact.config

import com.typesafe.config.{Config, ConfigFactory}
import collection.JavaConversions._
import scala.concurrent.duration._

class KadActConfig(val config: Config){
	def this() = 
		this(ConfigFactory.parseMap(
					Map("kadact.alpha" -> 3, 
							"kadact.B" -> 4, 
							"kadact.k" -> 2, 
							"kadact.s" -> 1, 
							"kadact.timeouts.nodeLookup" -> 5
							)))
	
	val alpha = config.getInt("kadact.alpha")//, 3)
	val B = config.getInt("kadact.B")//, 160)
	val k = config.getInt("kadact.k")//, 20)
	val s = config.getInt("kadact.s")//, 20)
	
	object Timeouts{
		val nodeLookup = config.getInt("kadact.timeouts.nodeLookup").seconds//, 10).seconds
		val storeValue = 5 seconds
	}
	
	val maxParallelLookups = 3
}