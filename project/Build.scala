import sbt._
import Keys._

import net.virtualvoid.sbt.graph.Plugin.graphSettings
import sbtassembly.Plugin._
import sbtassembly.Plugin.AssemblyKeys._
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._


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
  val DNS4S_VERSION           = "0.4-SNAPSHOT"
  val DNSJAVA_VERSION         = "2.1.6"
  val LOGBACK_VERSION         = "1.1.2"
  val PLAY_JSON_VERSION       = "2.2.3"
  val SLF4J_VERSION           = "1.7.6"
  val UNFILTERED_VERSION      = "0.7.1"
  val TYPESAFE_CONFIG_VERSION = "1.2.0"
  val SCALATEST_VERSION       = "2.1.5"


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
          Some("mesosphere.servicenet.http.HTTPServer"),
        mainClass in (Compile, run) :=
          Some("mesosphere.servicenet.http.HTTPServer")
      ) ++
      assemblySettings ++
      graphSettings
  ).dependsOn(dsl, http, ns, patch, util)
   .aggregate(dsl, http, ns, patch, util)

  def subproject(suffix: String) = s"${PROJECT_NAME}-$suffix"

  lazy val dsl = Project(
    id = subproject("dsl"),
    base = file("dsl"),
    settings = commonSettings
  ).dependsOn(util)

  lazy val http = Project(
    id = subproject("http"),
    base = file("http"),
    settings = commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "net.databinder"    %% "unfiltered-filter" % UNFILTERED_VERSION,
        "net.databinder"    %% "unfiltered-jetty"  % UNFILTERED_VERSION,
        "com.typesafe.play" %% "play-json"         % PLAY_JSON_VERSION
      )
    )
  ).dependsOn(dsl, util)

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
  ).dependsOn(dsl, util)

  lazy val patch = Project(
    id = subproject("patch"),
    base = file("patch"),
    settings = commonSettings
  ).dependsOn(dsl)

  lazy val util = Project(
    id = subproject("util"),
    base = file("util"),
    settings = commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-core"    % LOGBACK_VERSION,
        "ch.qos.logback" % "logback-classic" % LOGBACK_VERSION,
        "org.slf4j"      % "slf4j-api"       % SLF4J_VERSION
      )
    )
  )


  //////////////////////////////////////////////////////////////////////////////
  // SHARED SETTINGS
  //////////////////////////////////////////////////////////////////////////////

  lazy val commonSettings =
    Project.defaultSettings ++ basicSettings ++ formatSettings

  lazy val basicSettings = Seq(
    version := PROJECT_VERSION,
    organization := ORGANIZATION,
    scalaVersion := SCALA_VERSION,

    resolvers ++= Seq(
      "Mesosphere Repo"     at "http://downloads.mesosphere.io/maven",
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
    ),

    libraryDependencies ++= Seq(
      "com.typesafe"   % "config"    % TYPESAFE_CONFIG_VERSION,
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
