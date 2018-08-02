resolvers in ThisBuild ++= Seq("Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
  "velvia maven" at "http://dl.bintray.com/velvia/maven",
  Resolver.mavenLocal)

// settings definitions
val flinkVersion = settingKey[String]("Flink version")
val flinkDependencies = settingKey[Seq[String]]("Direct flink dependencies")

// The "main" project
lazy val root = (project in file("."))
  .settings(
	name := "SocketFlink",
	version := "1.99.8",
	organization := "net.juliobiason",
	flinkVersion := "1.4.0",
	scalaVersion := "2.11.8",
	mainClass := Some("net.juliobiason.flink.SocketWindowWordCount"),

	// dependencies
	libraryDependencies ++= Seq(
	  "commons-codec" % "commons-codec" % "1.11",
	  "mysql" % "mysql-connector-java" % "5.1.24",
	  "org.apache.flink" % "flink-core" % flinkVersion.value,
	  "org.apache.flink" % "flink-java" % flinkVersion.value,
	  "org.apache.flink" %% "flink-streaming-java" % flinkVersion.value % "provided",
	  "org.slf4j" % "slf4j-log4j12" % "1.7.25"
	),

	// as silly as it looks, this is what we need to do to run our 
	// pipeline as a standalone project (using Flink libraries, but
	// not Flink itself).
	run := Defaults.runTask(fullClasspath in Compile,
		mainClass in (Compile, run),
		runner in (Compile, run)).evaluated,

	javacOptions += "-Xlint:deprecation",
	javacOptions += "-Xlint:unchecked"
  )
