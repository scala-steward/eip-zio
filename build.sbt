name := "eip-zio"

version := "0.1.0"

scalaVersion := "2.13.0"

libraryDependencies ++= Seq(
  "dev.zio"       %% "zio"         % "1.0.0-RC11-1",
  "dev.zio"       %% "zio-streams" % "1.0.0-RC11-1",
  "org.scalatest" %% "scalatest"   % "3.0.8" % Test
)
