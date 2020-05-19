name := "eip-zio"
version := "DEV-SNAPSHOT"
scalaVersion := "2.13.0"
crossScalaVersions := Seq("2.12.8", "2.13.0")
libraryDependencies ++= Seq(
  "dev.zio"       %% "zio"         % "1.0.0-RC19-1",
  "dev.zio"       %% "zio-streams" % "1.0.0-RC19-1",
  "org.scalatest" %% "scalatest"   % "3.0.8" % Test
)

inThisBuild(
  List(
    organization := "net.kemitix",
    sonatypeProfileName := "net.kemitix",
    homepage := Some(url("https://github.com/kemitix/eip-zio")),
    licenses := List("mit" -> url("https://opensource.org/licenses/MIT")),
    developers := List(
      Developer(
        "kemitix",
        "Paul Campbell",
        "pcampbell@kemitix.net",
        url("https://github.kemitix.net")
      )
    ),
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:postfixOps",
      "-language:higherKinds"
    ),
    wartremoverErrors ++= Warts.unsafe.filterNot(
      wart =>
        List(
          Wart.Any,
          Wart.Nothing,
          Wart.Serializable,
          Wart.NonUnitStatements
        ).contains(wart))
  ))

