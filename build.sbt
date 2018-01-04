name := "dataroot-barbot"

version := "0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.1.4",
  "com.typesafe.slick" %% "slick" % "3.2.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.1",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  "com.typesafe.akka" %% "akka-http" % "10.0.11",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.11",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.19",
  "info.mukel" %% "telegrambot4s" % "3.0.14"
)