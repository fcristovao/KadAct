name := "KadAct"

version := "0.0"

scalaVersion := "2.10.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-actor" % "2.1.4",
	"com.typesafe.akka" %% "akka-remote" % "2.1.4", 
	"org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test"
)