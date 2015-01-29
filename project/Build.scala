import sbt._
import Keys._

object FirkinBuild  extends Build {
  val VERSION = "0.1.0"
  
  lazy val common = project settings(commonSettings : _*)

  lazy val server = project settings(serverSettings : _*) dependsOn(common)
  
  lazy val root = (project in file(".")).aggregate(common)
  
  lazy val serverRun = taskKey[Unit]("Run a Firkin server.")
  
  def baseSettings = Defaults.defaultSettings ++ Seq(
    organization := "com.freevariable",
    version := VERSION,
    scalaVersion := SCALA_VERSION,
    resolvers ++= Seq(
      "Akka Repo" at "http://repo.akka.io/repository",
      "spray" at "http://repo.spray.io/"
    ),
    scalacOptions ++= Seq("-feature", "-Yrepl-sync", "-target:jvm-1.7", "-Xlint")
  )
  
  def jsonSettings = Seq(
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % JSON4S_VERSION,
      "org.json4s" %% "json4s-ext" % JSON4S_VERSION,
      "joda-time" % "joda-time" % "2.5"
    ) 
  )

  def colossusSettings = Seq(
    libraryDependencies ++= Seq(
      "com.tumblr" %% "colossus" % "0.5.1",
      "com.typesafe.akka" %% "akka-actor"   % AKKA_VERSION,
      "com.typesafe.akka" %% "akka-agent"   % AKKA_VERSION,
      "com.typesafe.akka" %% "akka-testkit" % AKKA_VERSION
    ) 
  )
  
  def commonSettings = baseSettings ++ colossusSettings ++ jsonSettings
  
  def serverSettings = commonSettings ++ Seq(
    initialCommands in console := """
    import com.freevariable.firkin.Firkin
    val cache = Firkin.basicStart
    """
  )
  
  val SCALA_VERSION = "2.10.4"
  
  val JSON4S_VERSION = "3.2.10"
  
  val AKKA_VERSION = "2.3.9"
}
