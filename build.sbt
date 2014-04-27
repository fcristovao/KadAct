name := "KadAct"

version := "0.0"

scalaVersion := "2.10.3"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "org.scaldi" %% "scaldi" % "0.3",
	"com.typesafe.akka" %% "akka-actor" % "2.3.0",
	"com.typesafe.akka" %% "akka-remote" % "2.3.0",
	"com.typesafe.akka" %% "akka-testkit" % "2.3.0",
	"org.scalamock" %% "scalamock-scalatest-support" % "3.0.1" % "test", 
	"org.scalatest" % "scalatest_2.10" % "2.1.4" % "test"
)