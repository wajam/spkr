import sbt._
import Keys._
import com.typesafe.sbt.SbtStartScript

//TODO change the name of the object to reflect your project name.
object ScalabootBuild extends Build {
  val PROJECT_NAME = "scalaboot" //TODO change this!

  var commonResolvers = Seq(
    // local snapshot support
    ScalaToolsSnapshots,

    // common deps
    "Wajam" at "http://ci1.cx.wajam/",
    "Maven.org" at "http://repo1.maven.org/maven2",
    "Sun Maven2 Repo" at "http://download.java.net/maven/2",
    "Scala-Tools" at "http://scala-tools.org/repo-releases/",
    "Sun GF Maven2 Repo" at "http://download.java.net/maven/glassfish",
    "Oracle Maven2 Repo" at "http://download.oracle.com/maven",
    "Sonatype" at "http://oss.sonatype.org/content/repositories/release",
    "spy" at "http://files.couchbase.com/maven2/",
    "Twitter" at "http://maven.twttr.com/"
  )

  var commonDeps = Seq(
    "com.wajam" %% "nrv-core" % "0.1-SNAPSHOT",
    "org.scalatest" %% "scalatest" % "1.7.1" % "test,it",
    "junit" % "junit" % "4.10" % "test,it",
    "org.mockito" % "mockito-core" % "1.9.0" % "test,it"
  )

  val defaultSettings = Defaults.defaultSettings ++ Defaults.itSettings ++ Seq(
    libraryDependencies ++= commonDeps,
    resolvers ++= commonResolvers,
    retrieveManaged := true,
    publishMavenStyle := true,
    organization := "com.wajam",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.9.1"
  )

  lazy val root = Project(PROJECT_NAME, file("."))
    .configs(IntegrationTest)
    .settings(defaultSettings: _*)
    .settings(testOptions in IntegrationTest := Seq(Tests.Filter(s => s.contains("Test"))))
    .settings(parallelExecution in IntegrationTest := false)
    .settings(SbtStartScript.startScriptForClassesSettings: _*)
    .aggregate(core)

  lazy val core = Project(PROJECT_NAME+"-core", file(PROJECT_NAME+"-core"))
    .configs(IntegrationTest)
    .settings(defaultSettings: _*)
    .settings(testOptions in IntegrationTest := Seq(Tests.Filter(s => s.contains("Test"))))
    .settings(parallelExecution in IntegrationTest := false)
    .settings(SbtStartScript.startScriptForClassesSettings: _*)
}
