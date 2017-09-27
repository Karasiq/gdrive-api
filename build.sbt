lazy val commonSettings = Seq(
  organization := "com.github.karasiq",
  version := "1.0.11",
  isSnapshot := version.value.endsWith("SNAPSHOT"),
  // resolvers += Resolver.sonatypeRepo("snapshots"),
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq("2.11.11", "2.12.3")
)

lazy val librarySettings = Seq(
  name := "gdrive-api",
  libraryDependencies ++= Seq(
    "com.typesafe" % "config" % "1.3.1",
    "com.typesafe.akka" %% "akka-actor" % "2.5.4" % "provided",
    "com.google.api-client" % "google-api-client" % "1.22.0",
    "com.google.apis" % "google-api-services-drive" % "v3-rev82-1.22.0",
    "com.google.oauth-client" % "google-oauth-client-jetty" % "1.22.0",
    "com.github.karasiq" %% "commons-configs" % "1.0.8"
  )
)

lazy val testAppSettings = Seq(
  libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.4"
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ ⇒ false },
  licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0")),
  homepage := Some(url("https://github.com/Karasiq/gdrive-api")),
  pomExtra := <scm>
    <url>git@github.com:Karasiq/gdrive-api.git</url>
    <connection>scm:git:git@github.com:Karasiq/gdrive-api.git</connection>
  </scm>
    <developers>
      <developer>
        <id>karasiq</id>
        <name>Piston Karasiq</name>
        <url>https://github.com/Karasiq</url>
      </developer>
    </developers>
)

lazy val noPublishSettings = Seq(
  publishArtifact := false,
  publishArtifact in makePom := false,
  publishTo := Some(Resolver.file("Repo", file("target/repo")))
)

lazy val library = project
  .settings(commonSettings, librarySettings, publishSettings)

lazy val testApp = (project in file("test-app"))
  .settings(commonSettings, testAppSettings)
  .dependsOn(library)

lazy val root = (project in file("."))
  .settings(commonSettings, noPublishSettings)
  .aggregate(library)