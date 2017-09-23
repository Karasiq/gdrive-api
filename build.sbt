organization := "com.github.karasiq"

name := "gdrive-api"

version := "1.0.0-SNAPSHOT"

isSnapshot := version.value.endsWith("SNAPSHOT")

resolvers += Resolver.sonatypeRepo("snapshots")

scalaVersion := "2.12.3"

crossScalaVersions := Seq("2.11.11", "2.12.3")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.4",
  "com.google.api-client" % "google-api-client" % "1.22.0",
  "com.google.apis" % "google-api-services-drive" % "v3-rev82-1.22.0",
  "com.google.oauth-client" % "google-oauth-client-jetty" % "1.22.0",
  "com.github.karasiq" %% "commons-configs" % "1.0.8-SNAPSHOT"
)