name := "dns-comparison"

val playWsStandaloneVersion = "1.0.0-RC4"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsStandaloneVersion,
  "com.typesafe.play" %% "play-ws-standalone-json" % playWsStandaloneVersion,
  "dnsjava" % "dnsjava" % "2.1.7",
  "org.typelevel" %% "cats" % "0.8.1",
  "com.github.scopt" %% "scopt" % "3.6.0"
)