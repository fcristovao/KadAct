package kadact.node

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.testkit.TestKit
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
import akka.testkit.ImplicitSender

class NodeFSMTest extends TestKit(ActorSystem("NodeFSMTest")) with ImplicitSender
  with WordSpec with MustMatchers with BeforeAndAfterAll {
 
  override def afterAll {
    system.shutdown()
  }
 
  "An Echo actor" must {
    "send back messages unchanged" in {
      pending
    }
 
  }
}