name := "cenaculo"

version := "1.0"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
    "org.im4java" % "im4java" % "1.4.0",
    "com.typesafe" % "config" % "1.3.1",
    "com.github.pathikrit" %% "better-files" % "2.17.1",
    "org.scala-lang.modules" %% "scala-pickling" % "0.10.1",
    "org.apache.commons" % "commons-imaging" % "1.0",
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
)
