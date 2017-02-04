name := """play-scala"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
resolvers += Resolver.url("Typesafe Ivy releases", url("https://repo.typesafe.com/typesafe/ivy-releases"))(Resolver.ivyStylePatterns)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "org.typelevel" %% "cats" % "0.9.0",
  "com.github.etaty" %% "rediscala" % "1.8.0",
  "de.leanovate.play-mockws" % "play-mockws_2.11" % "2.5.1",
  "org.mockito" % "mockito-all" % "1.9.5"
)



fork in run := false