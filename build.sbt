name := "akarnokd-misc-scala"

version := "1.0"

scalaVersion := "2.11.8"

resolvers +=
  "JFrog OSS Snapshots" at "https://oss.jfrog.org/libs-snapshot"

resolvers +=
  "Spring Snapshots" at "http://repo.spring.io/snapshot"

resolvers +=
  "Sonatype Public" at "https://oss.sonatype.org/content/groups/public"


libraryDependencies ++= Seq(
  "io.swave" %% "swave-core"          % "0.6.0",
  "io.reactivex" % "rxjava" % "1.2.4",
  //"io.reactivex.rxjava2" % "rxjava" % "2.0.4"
  "io.reactivex.rxjava2" % "rxjava" % "2.0.0-DP0-SNAPSHOT",
  "com.github.akarnokd" % "ixjava" % "1.0.0-RC5",
  "io.projectreactor" % "reactor-core" % "3.0.4.BUILD-SNAPSHOT"
)

libraryDependencies +=
  "com.github.akarnokd" % "rxjava2-extensions" % "0.14.2" exclude("io.reactivex.rxjava2", "rxjava")

libraryDependencies +=
  "com.typesafe.akka"          %%  "akka-stream"           % "2.4.16"