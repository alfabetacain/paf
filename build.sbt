import Dependencies._

ThisBuild / scalaVersion     := "2.13.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "dk.alfabetacain"
ThisBuild / organizationName := "alfabetacain"

lazy val declineVersion = "2.2.0"
lazy val scribeVersion  = "3.10.1"

lazy val root = (project in file("."))
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    name                            := "paf2",
    libraryDependencies += scalaTest % Test,
    libraryDependencies ++= Seq(
      "org.typelevel"       %% "cats-effect"    % "3.3.12",
      "com.monovore"        %% "decline"        % declineVersion,
      "com.monovore"        %% "decline-effect" % declineVersion,
      "com.lihaoyi"         %% "os-lib"         % "0.8.0",
      "org.scodec"          %% "scodec-core"    % "1.11.9",
      "com.outr"            %% "scribe"         % scribeVersion,
      "com.outr"            %% "scribe-slf4j"   % scribeVersion,
      "com.outr"            %% "scribe-cats"    % scribeVersion,
      "com.disneystreaming" %% "weaver-cats"    % "0.7.12" % Test
    ),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
    Test / fork                    := true,
    graalVMNativeImageGraalVersion := Some("22.1.0"),
    graalVMNativeImageOptions += "--no-fallback"
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
