// *****************************************************************************
// Projects
// *****************************************************************************

lazy val accessus =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin, GitVersioning)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.akkaHttp,
        library.akkaStream,
        library.mockitoCore % Test,
        library.scalaTest   % Test
      )
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val akka      = "2.5.2"
      val akkaHttp  = "10.0.7"
      val mockito   = "2.8.9"
      val scalaTest = "3.0.3"
    }
    val akkaHttp    = "com.typesafe.akka" %% "akka-http"    % Version.akkaHttp
    val akkaStream  = "com.typesafe.akka" %% "akka-stream"  % Version.akka
    val mockitoCore = "org.mockito"       %  "mockito-core" % Version.mockito
    val scalaTest   = "org.scalatest"     %% "scalatest"    % Version.scalaTest
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  commonSettings ++
  gitSettings

lazy val commonSettings =
  Seq(
    // scalaVersion from .travis.yml via sbt-travisci
    // scalaVersion := "2.12.2",
    organization := "de.heikoseeberger",
    organizationName := "Heiko Seeberger",
    startYear := Some(2017),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8"
    ),
    unmanagedSourceDirectories.in(Compile) := Seq(scalaSource.in(Compile).value),
    unmanagedSourceDirectories.in(Test) := Seq(scalaSource.in(Test).value),
    shellPrompt in ThisBuild := { state =>
      val project = Project.extract(state).currentRef.project
      s"[$project]> "
    }
)

lazy val gitSettings =
  Seq(
    git.useGitDescribe := true
  )
