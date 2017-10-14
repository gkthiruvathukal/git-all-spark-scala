name := "git-all-spark-scala"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

mainClass in assembly := Some("se_hpc.GitAllSparkScala")

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

)

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.2.0" % "provided",
  "com.github.scopt" %% "scopt" % "3.6.0",
  "com.novocode" % "junit-interface" % "latest.release" % Test,
  "org.scalatest" %% "scalatest" % "latest.release" % Test,
  "com.lihaoyi" %% "ammonite-ops" % "1.0.0",
  "com.lihaoyi" %% "pprint" % "0.4.3",
  "com.chuusai" %% "shapeless" % "2.3.2"
)
