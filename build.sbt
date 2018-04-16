// *****************************************************************************
// Projects
// *****************************************************************************

lazy val accessus =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.akkaHttp,
        library.akkaStream,
        library.utest       % Test
      )
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val akka      = "2.5.12"
      val akkaHttp  = "10.1.1"
      val utest     = "0.6.4"
    }
    val akkaHttp   = "com.typesafe.akka" %% "akka-http"   % Version.akkaHttp
    val akkaStream = "com.typesafe.akka" %% "akka-stream" % Version.akka
    val utest      = "com.lihaoyi"       %% "utest"       % Version.utest
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  commonSettings ++
  scalafmtSettings ++
  publishSettings

lazy val commonSettings =
  Seq(
    scalaVersion := "2.12.4",
    organization := "rocks.heikoseeberger",
    organizationName := "Heiko Seeberger",
    startYear := Some(2018),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8",
      "-Ypartial-unification",
      "-Ywarn-unused-import"
    ),
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    Test / unmanagedSourceDirectories := Seq((Test / scalaSource).value),
    testFrameworks += new TestFramework("utest.runner.Framework")
)

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true
  )

lazy val publishSettings =
  Seq(
    homepage := Some(url("https://github.com/hseeberger/accessus")),
    scmInfo := Some(ScmInfo(url("https://github.com/hseeberger/accessus"),
                            "git@github.com:hseeberger/accessus.git")),
    developers += Developer("hseeberger",
                            "Heiko Seeberger",
                            "mail@heikoseeberger.de",
                            url("https://github.com/hseeberger")),
    pomIncludeRepository := (_ => false)
  )
