name := "akarnokd-misc-scala"

version := "1.0"

scalaVersion := "2.11.8"

resolvers +=
  "JFrog OSS Snapshots" at "https://oss.jfrog.org/libs-snapshot"

libraryDependencies ++= Seq(
  "io.swave" %% "swave-core"          % "0.6.0",
  "io.reactivex" % "rxjava" % "1.2.4",
  //"io.reactivex.rxjava2" % "rxjava" % "2.0.3"
  "io.reactivex.rxjava2" % "rxjava" % "2.0.0-DP0-SNAPSHOT"
)