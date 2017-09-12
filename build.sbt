name := "git-all-spark-scala"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

mainClass in assembly := Some("se_hpc.GitAllSparkScala")

resolvers ++= Seq(
//  "gkthiruvathukal@bintray" at "http://dl.bintray.com/gkthiruvathukal/maven",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.2.0" % "provided",
//  "org.scalanlp" %% "breeze" % "0.11.2" % "provided",
//  "org.scalanlp" %% "breeze-natives" % "0.11.2" % "provided",
//  "org.scalanlp" %% "breeze-viz" % "0.11.2" % "provided",
//  "org.scalatest" %% "scalatest" % "2.2.6" % Test,
  "com.github.scopt" %% "scopt" % "3.6.0",
  "com.novocode" % "junit-interface" % "latest.release" % Test,
  "org.scalatest" %% "scalatest" % "latest.release" % Test,
  "com.lihaoyi" %% "ammonite-ops" % "1.0.0"
)
