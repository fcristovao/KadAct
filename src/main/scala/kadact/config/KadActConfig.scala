package kadact.config

import com.typesafe.config.Config
import scala.concurrent.duration._

class KadActConfig(val config: Config) {
  //3
  val B = config.getInt("kadact.B")
  //160
  val k = config.getInt("kadact.k")
  //20
  val s = config.getInt("kadact.s")
  //20
  val alpha = config.getInt("kadact.alpha")

  object Timeouts {
    //10
    val nodeLookup = config.getInt("kadact.timeouts.nodeLookup").seconds
    val storeValue = 5 seconds
  }

  val maxParallelLookups = 3
}