package kadact.node.routing

import scala.collection.JavaConversions.mapAsJavaMap
import scala.math.BigInt.int2bigInt

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

import com.typesafe.config.ConfigFactory

import kadact.config.KadActConfig
import kadact.node.distance

class IdDistanceSpaceSpec extends FunSpec with ShouldMatchers {
	
	implicit val config = new KadActConfig(ConfigFactory.parseMap(
																			Map("kadact.alpha" -> 3, 
																					"kadact.B" -> 4, 
																					"kadact.k" -> 2, 
																					"kadact.s" -> 1, 
																					"kadact.timeouts.nodeLookup" -> 5
																					)))
	
	describe("An IdDistanceSpace") {
		it("should contain all the range of Ids when empty") {
			val distanceSpace = IdDistanceSpace(0)
			
			distanceSpace.contains(distance(0, BigInt(2).pow(config.B) - 1)) should equal(true)
		}
	}

}