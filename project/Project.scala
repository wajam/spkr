import sbt._
import Keys._
import com.typesafe.sbt.SbtStartScript

//TODO change the name of the object to reflect your project name.
object SpkBuild extends Build {
  val PROJECT_NAME = "spk"

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
    "Cloudera" at "https://repository.cloudera.com/artifactory/cloudera-repos/",
    "Twitter" at "http://maven.twttr.com/"
  )

  var commonDeps = Seq(
    "org.slf4j" % "slf4j-log4j12" % "1.6.4",
    "commons-configuration" % "commons-configuration" % "1.6",
    "log4j" % "log4j" % "1.2.15" exclude("javax.jms", "jms") exclude("com.sun.jmx", "jmxri") exclude("com.sun.jdmk", "jmxtools"),
    "com.wajam" %% "nrv-core" % "0.1-SNAPSHOT" exclude("org.slf4j", "slf4j-nop"),
    "com.wajam" %% "nrv-scribe" % "0.1-SNAPSHOT",
    "com.wajam" %% "nrv-extension" % "0.1-SNAPSHOT",
    "com.wajam" %% "scn-core" % "0.1-SNAPSHOT",
    "com.wajam" %% "spnl-core" % "0.1-SNAPSHOT",
    "com.wajam" %% "mry-core" % "0.1-SNAPSHOT",
    "com.google.guava" % "guava" % "12.0",
    "c3p0" % "c3p0" % "0.9.1.2",
    "mysql" % "mysql-connector-java" % "5.1.6",
    "spy" % "spymemcached" % "2.6",
    "nl.grons" %% "metrics-scala" % "2.2.0" exclude("org.slf4j", "slf4j-api"),
    "net.liftweb" %% "lift-json" % "2.5-RC4",
    "org.scalatest" %% "scalatest" % "1.9.1" % "test,it",
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
    scalaVersion := "2.10.2"
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
