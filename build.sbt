name := "KadAct"

version := "0.0"

scalaVersion := "2.9.1"

unmanagedBase <<= baseDirectory { base => base / "lib/akka" }