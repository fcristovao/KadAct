package kadact

import org.scalatest.FunSpec
import kadact.config.KadActConfig
import scala.collection.JavaConversions.mapAsJavaMap
import com.typesafe.config.ConfigFactory

class KadActSpec extends FunSpec {

	implicit val config = new KadActConfig(ConfigFactory.parseMap(
																			Map("kadact.alpha" -> 3, 
																					"kadact.B" -> 4, 
																					"kadact.k" -> 2, 
																					"kadact.s" -> 1, 
																					"kadact.timeouts.nodeLookup" -> 5
																					)))
	
	describe("Kadact") {
		ignore("should be initializable") {
			new KadAct[Int]("localhost", 55372)
		}
		
		it("should be possible to add content to the network") {
			val kadact = new KadAct[Int]("localhost", 55373)
			kadact.start()
			kadact.add(0,1)
		}
	}
}