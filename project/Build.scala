import sbt._
import Keys._

import net.virtualvoid.sbt.graph.Plugin.graphSettings
import sbtassembly.Plugin._
import sbtassembly.Plugin.AssemblyKeys._
import com.typesafe.sbt.SbtScalariform._
import ohnosequences.sbt.SbtS3Resolver.S3Resolver
import ohnosequences.sbt.SbtS3Resolver.{ s3, s3resolver }
import scalariform.formatter.preferences._
import sbtunidoc.Plugin._

object ServiceNetBuild extends Build {

  //////////////////////////////////////////////////////////////////////////////
  // PROJECT INFO
  //////////////////////////////////////////////////////////////////////////////

  val ORGANIZATION = "mesosphere"
  val PROJECT_NAME = "service-net"
  val PROJECT_VERSION = "0.1.0-SNAPSHOT"
  val SCALA_VERSION = "2.10.4"


  //////////////////////////////////////////////////////////////////////////////
  // DEPENDENCY VERSIONS
  //////////////////////////////////////////////////////////////////////////////

  val AKKA_VERSION            = "2.3.2"
  val DISPATCH_VERSION        = "0.11.1"
  val DNS4S_VERSION           = "0.4"
  val DNSJAVA_VERSION         = "2.1.6"
  val LOGBACK_VERSION         = "1.1.2"
  val PLAY_JSON_VERSION       = "2.2.3"
  val SLF4J_VERSION           = "1.7.6"
  val UNFILTERED_VERSION      = "0.7.1"
  val SCALATEST_VERSION       = "2.1.5"
  val FINAGLE_VERSION         = "6.16.0"
  val SCALA_URI_VERSION       = "0.3.6"
  val H2_VERSION              = "1.4.178"
  val SCOPT_VERSION           = "3.2.0"

  //////////////////////////////////////////////////////////////////////////////
  // PROJECTS
  //////////////////////////////////////////////////////////////////////////////

  lazy val root = Project(
    id = PROJECT_NAME,
    base = file("."),
    settings = commonSettings ++
      Seq(
        aggregate in update := false,
        mainClass in (Compile, packageBin) :=
          Some("mesosphere.servicenet.daemon.ServiceNet"),
        mainClass in (Compile, run) :=
          Some("mesosphere.servicenet.daemon.ServiceNet")
      ) ++
      assemblySettings ++
      graphSettings
  ).dependsOn(daemon, dsl, http, ns, patch, config, util)
   .aggregate(daemon, dsl, http, ns, patch, config, util, tests)

  def subproject(suffix: String) = s"${PROJECT_NAME}-$suffix"

  lazy val daemon = Project(
    id = subproject("daemon"),
    base = file("daemon"),
    settings = commonSettings
  ).dependsOn(http, ns, patch, config, util)

  lazy val dsl = Project(
    id = subproject("dsl"),
    base = file("dsl"),
    settings = commonSettings
  ).dependsOn(util % "test->test;compile->compile")

  lazy val http = Project(
    id = subproject("http"),
    base = file("http"),
    settings = commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "net.databinder"    %% "unfiltered-filter" % UNFILTERED_VERSION,
        "net.databinder"    %% "unfiltered-jetty"  % UNFILTERED_VERSION,
        "com.typesafe.play" %% "play-json"         % PLAY_JSON_VERSION,
        "com.h2database"     % "h2"                % H2_VERSION
      )
    )
  ).dependsOn(dsl, config, util % "test->test;compile->compile")

  lazy val ns = Project(
    id = subproject("ns"),
    base = file("ns"),
    settings = commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % AKKA_VERSION,
        "com.github.mkroli" %% "dns4s-akka" % DNS4S_VERSION,
        "dnsjava"            % "dnsjava"    % DNSJAVA_VERSION
      )
    )
  ).dependsOn(dsl, config, util % "test->test;compile->compile")

  lazy val patch = Project(
    id = subproject("patch"),
    base = file("patch"),
    settings = commonSettings
  ).dependsOn(dsl, config, util)

  lazy val config = Project(
    id = subproject("config"),
    base = file("config"),
    settings = commonSettings
  ).dependsOn(dsl, util)

  lazy val util = Project(
    id = subproject("util"),
    base = file("util"),
    settings = commonSettings ++ Seq(

      publishArtifact in Test := true,

      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-core"    % LOGBACK_VERSION,
        "ch.qos.logback" % "logback-classic" % LOGBACK_VERSION,
        "org.slf4j"      % "slf4j-api"       % SLF4J_VERSION
      )
    )
  )

  lazy val tests = Project(
    id = subproject("tests"),
    base = file("tests"),
    settings = commonSettings ++
      Seq(
        aggregate in update := false,
        mainClass in (Compile, packageBin) :=
          Some("mesosphere.servicenet.tests.Test"),
        mainClass in (Compile, run) :=
          Some("mesosphere.servicenet.tests.Test")
      ) ++
      assemblySettings ++
      graphSettings ++
      Seq(
        libraryDependencies ++= Seq(
          "net.databinder"    %% "unfiltered-filter" % UNFILTERED_VERSION,
          "net.databinder"    %% "unfiltered-jetty"  % UNFILTERED_VERSION,
          "com.typesafe.play" %% "play-json"         % PLAY_JSON_VERSION,
          "com.twitter"       %% "finagle-http"      % FINAGLE_VERSION,
          "com.twitter"       %% "finagle-stats"     % FINAGLE_VERSION,
          "com.github.theon"  %% "scala-uri"         % SCALA_URI_VERSION,
          "com.github.scopt"  %% "scopt"             % SCOPT_VERSION
        )
      )
  ).dependsOn(dsl, util, http)

  //////////////////////////////////////////////////////////////////////////////
  // SHARED SETTINGS
  //////////////////////////////////////////////////////////////////////////////

  lazy val commonSettings =
    Project.defaultSettings ++
    basicSettings ++
    formatSettings ++
    unidocSettings ++
    publishSettings

  lazy val basicSettings = Seq(
    version := PROJECT_VERSION,
    organization := ORGANIZATION,
    scalaVersion := SCALA_VERSION,

    resolvers ++= Seq(
      "Twitter"             at "http://maven.twttr.com/",
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
    ),

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % SCALATEST_VERSION % "test"
    ),

    scalacOptions in Compile ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature"
    ),

    javacOptions in Compile ++= Seq(
      "-Xlint:unchecked",
      "-source", "1.7",
      "-target", "1.7"
    ),

    fork in Test := false
  )

  lazy val publishSettings = S3Resolver.defaults ++ Seq(
    publishTo := Some(s3resolver.value(
      "Mesosphere Public Repo",
      s3("downloads.mesosphere.io/maven")
    ))
  )

  lazy val formatSettings = scalariformSettings ++ Seq(
    ScalariformKeys.preferences := FormattingPreferences()
      .setPreference(IndentWithTabs, false)
      .setPreference(IndentSpaces, 2)
      .setPreference(AlignParameters, true)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
      .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true)
      .setPreference(PreserveDanglingCloseParenthesis, true)
      .setPreference(CompactControlReadability, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(PreserveSpaceBeforeArguments, true)
      .setPreference(SpaceBeforeColon, false)
      .setPreference(SpaceInsideBrackets, false)
      .setPreference(SpaceInsideParentheses, false)
      .setPreference(SpacesWithinPatternBinders, true)
      .setPreference(FormatXml, true)
    )

}
