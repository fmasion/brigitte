name:= "brigitte"

scalaVersion := "2.12.4"

version := "0.2.1"

crossScalaVersions := Seq("2.11.11", "2.12.4")

organization := "com.kreactive"

scalacOptions ++= Seq("-deprecation")

bintrayOrganization in ThisBuild := Some("kreactive")

licenses in ThisBuild := List(
  ("Apache-2.0",
    url("https://www.apache.org/licenses/LICENSE-2.0"))
)

homepage in ThisBuild := Some(url("https://github.com/kreactive"))

publishTo := Some("kreactive bintray" at "https://api.bintray.com/maven/kreactive/maven/brigitte")

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

publishMavenStyle := true

bintrayReleaseOnPublish in ThisBuild := false

lazy val brigitte = project.in(file("."))

val betterFiles: SettingKey[String] = SettingKey[String]("betterFilesVersion", "Version for better files, depending on scalaVersion")

betterFiles := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 11)) => "2.16.0"
    case Some((2, 12)) => "3.1.0"
    case _ => "3.1.0"
  }
}

resolvers += Resolver.bintrayRepo("kreactive", "maven")

libraryDependencies ++= Seq (
  "com.github.pathikrit" %% "better-files" % betterFiles.value,
  "com.chuusai" %% "shapeless" % Version.shapeless,
  "com.github.kxbmap" %% "configs" % "0.4.4",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.8.0.201706111038-r",
  "com.typesafe.akka" %% "akka-stream" % Version.akka,
  "com.typesafe.akka" %% "akka-http" % Version.akkaHttp,
  "com.kreactive" %% "pactole-http" % Version.pactole
)