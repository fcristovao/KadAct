package kadact

import org.scalatest.WordSpec
import kadact.config.KadActConfig
import scala.collection.JavaConversions.mapAsJavaMap
import com.typesafe.config.ConfigFactory

class KadActSpec extends WordSpec {

	implicit val config = new KadActConfig(ConfigFactory.parseMap(
																			Map("kadact.alpha" -> 3, 
																					"kadact.B" -> 4, 
																					"kadact.k" -> 2, 
																					"kadact.s" -> 1, 
																					"kadact.timeouts.nodeLookup" -> 5
																					)))
	
	def createKadActNetwork(howManyNodes: Int) = {
		require(howManyNodes > 0)
		val result = new Array[KadAct[Int]](howManyNodes)
		val startingPort = 55372
		
		result(0) = new KadAct[Int]("127.0.0.1", startingPort)
		result(0).start()
		Thread.sleep(5000)
		
		for(currentIndex <- 1 to (howManyNodes - 1)) {
			result(currentIndex) = new KadAct[Int]("127.0.0.1", startingPort + currentIndex)
			result(currentIndex).join("127.0.0.1", startingPort + currentIndex - 1)
			Thread.sleep(5000)
		}
		result
	}
	
	def stopKadActNetwork(network: Array[KadAct[Int]]) = {
		for(index <- (network.length - 1) to 1 by -1){
			network(index).stop()
			Thread.sleep(1000)
		}
	}
	
	
	"A KadAct node" should {
		"be initializable" ignore {
			new KadAct[Int]("localhost", 55372)
		}
		
		"be able to add content to the network" ignore {
			val kadact = new KadAct[Int]("localhost", 55373)
			kadact.start()
			kadact.add(0,1)
		}
		
		"be able to join a network of KadAct nodes" in {
			val kadActNetwork = createKadActNetwork(2)
			stopKadActNetwork(kadActNetwork)
		}
	}
	
	
}