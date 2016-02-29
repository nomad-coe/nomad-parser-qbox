/** to build the first time you need to execute
  * jooq:codegen which depends on flywayMigrate to generate the jooq db code
  */
scalaVersion  := "2.11.7"

// # libs

// ## input configuration
val configLib     = "com.typesafe"        % "config"         % "1.2.1"

// ## logging libs
val loggingLibs = {
  val scalalogLib      = "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
  val log4j2Libs       = Seq(
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.5",
    "org.apache.logging.log4j" % "log4j-api"        % "2.5",
    "org.apache.logging.log4j" % "log4j-core"       % "2.5",
    "org.apache.logging.log4j" % "log4j-1.2-api"    % "2.5")
  scalalogLib +: log4j2Libs
}

// ## test libs
val testLibs         = {
  val specs2Lib        = "org.specs2"         %% "specs2-core"    % "2.3.11" % "test"
  val scalacheckLib    = "org.scalacheck"     %% "scalacheck"     % "1.12.4" % "test"
  Seq(specs2Lib, scalacheckLib)
}

// ## json libs
// json4s 3.3 is being finalized
val json4sNativeLib  = "org.json4s"         %% "json4s-native"  % "3.2.11"
val json4sJacksonLib = "org.json4s"         %% "json4s-jackson" % "3.2.11"

// ## mime type recognition lib
val tikaLib          = "org.apache.tika"     % "tika-core"       % "1.10"

// ## compression handling libs
val compressionLibs = {
  val commonsCompressLib = "org.apache.commons"  % "commons-compress" % "1.10"
  val xzForJavaLib       = "org.tukaani"         % "xz"               % "1.5"
  Seq(commonsCompressLib, xzForJavaLib)
}

// ## archive management libs
val bagitLib = "gov.loc" % "bagit" % "4.11.0"

// ## queuing libs
val kafkaLib         = "org.apache.kafka"   %% "kafka"           % "0.9.0.0" exclude("log4j", "log4j") exclude("org.slf4j","slf4j-log4j12")
val rabbitmqLib      = "com.rabbitmq"        % "amqp-client"      %"3.6.0"


// ## markdown interpreter lib
val pegdownLib       = "org.pegdown"         % "pegdown"         % "1.6.0"

// ## spray and akka (web service and actors)
lazy val sprayLibs = {
  val akkaV = "2.3.9"
  val sprayV = "1.3.3"
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test")
}

// ## db libs
val h2Lib            = "com.h2database"      % "h2"             % "1.4.187"
val postgresLib      = "org.postgresql"      % "postgresql"     % "9.4.1207.jre7"
val flywayLib        = "org.flywaydb"        % "flyway-core"    % "3.2.1"
val jooqLibVersion   = "3.6.2"
val jooqLib          = "org.jooq"            % "jooq"            % jooqLibVersion
val jooqMetaLib      = "org.jooq"            % "jooq-meta"            % jooqLibVersion

// discarded libs
// val re2j          = "com.google.re2j"     % "re2j"            % "1.0" // faster regexps
// val fastring      = "com.dongxiguo"      %% "fastring"        % "0.2.4" // faster string templates
// val playJson      = "com.typesafe.play"  %% "play-json"       % "2.4.3" // json4s alternative
val netcdf        = "edu.ucar"            % "netcdf4"         % "4.6.3" // pure java netcdf lib (writes only netcdf3, so full version in unmanaged jars)

val versionRe = """v([0-9]+(?:\.[0-9]+(?:\.[0-9]+)?)?)-?(.*)?""".r

lazy val commonSettings = Seq(
  organization  := "eu.nomad-laboratory",
  scalaVersion  := "2.11.7",
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature"),
  // resolvers     += "netcdf releases" at "http://artifacts.unidata.ucar.edu/content/repositories/unidata-releases",
  libraryDependencies ++= testLibs,
  libraryDependencies ++= loggingLibs,
  fork in run   := true,
  fork in Test  := true // breaks test summary report */
)

lazy val gitVersionSettings = Seq(
  buildInfoPackage := "eu.nomad_lab",
  buildInfoOptions += BuildInfoOption.ToMap,
  buildInfoObject := name.value.capitalize + "VersionInfo",
  version := {
    val gitV: String = Process("git" :: "describe" :: "--tags" :: "--dirty" :: Nil, baseDirectory.value) !!;
    gitV.trim
  },
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    version,
    scalaVersion,
    sbtVersion,
    BuildInfoKey.action("buildTime") {
      System.currentTimeMillis
    })
)


lazy val jooqCommon = seq(jooqSettings:_*) ++ Seq(
  jooqVersion := "3.6.2"
)

lazy val rdbUrl = settingKey[String]("url to the rdb used during building")

//libraryDependencies ++= loggingLibs

lazy val rdbGen = (project in file("rdb/rdb-gen")).
  settings(commonSettings: _*).
  settings(
    name := "rdbGen"
  ).
  settings(flywaySettings: _*).
  settings(jooqCommon: _*).
  settings(
    rdbUrl := {
    "jdbc:h2:file:" + ((resourceManaged in Compile).value / "localdb_h2")
  } ).
  settings(
    flywayUrl := rdbUrl.value,
    flywayLocations := Seq( "classpath:rdb/migrations")
  ).
  settings(jooqCommon: _*).
  settings(
    (codegen in JOOQ) <<= (codegen in JOOQ).dependsOn(flywayMigrate),
    libraryDependencies +=  h2Lib % "jooq",
    jooqOptions := Seq(
      "jdbc.driver" -> "org.h2.Driver",
      "jdbc.url" -> rdbUrl.value,
      "generator.database.name" -> "org.jooq.util.h2.H2Database",
      "generator.database.inputSchema" -> "PUBLIC",
      "generator.database.outputSchemaToDefault" -> "true",
      "generator.database.includes" -> ".*",
      "generator.target.packageName" -> "eu.nomad_lab.rdb.db",
      "generator.generate.pojos" -> "false",
      "generator.generate.immutablePojos" -> "false",
      "generator.generate.deprecated" -> "false"
    ),
    jooqOutputDirectory := (baseDirectory.value / "../rdb/src/main/java").getCanonicalFile()
  )

lazy val rdb = (project in file("dbs/rdb")).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    name := "rdb",
    libraryDependencies ++= (
      configLib +:
        h2Lib +:
        jooqLib +:
        flywayLib +:
        loggingLibs),
    (unmanagedResourceDirectories in Compile) += (baseDirectory.value / "../rdb-gen/src/main/resources").getCanonicalFile()
  )

lazy val core = (project in file("core")).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (
      configLib +:
        json4sNativeLib +:
        json4sJacksonLib +:
        pegdownLib +:
        tikaLib +:
        kafkaLib +:
        netcdf +:
        compressionLibs ++:
        loggingLibs),
    name := "nomadCore",
    (unmanagedResourceDirectories in Compile) += (baseDirectory.value / "../nomad-meta-info/meta_info").getCanonicalFile(),
    (unmanagedResourceDirectories in Compile) += (baseDirectory.value / "../python-common/common").getCanonicalFile()
  )

lazy val fhiAims = (project in file("parsers/fhi-aims")).
  dependsOn(core).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    name := "fhiAims"
  ).
  settings(Revolver.settings: _*)

lazy val castep = (project in file("parsers/castep")).
  dependsOn(core).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    name := "castep",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val cp2k = (project in file("parsers/cp2k")).
  dependsOn(core).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    name := "cp2k",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val dlPoly = (project in file("parsers/dl-poly")).
  dependsOn(core).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    name := "dlPoly",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val exciting = (project in file("parsers/exciting")).
  dependsOn(core).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    name := "exciting",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val gaussian = (project in file("parsers/gaussian")).
  dependsOn(core).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    name := "gaussian",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val gpaw = (project in file("parsers/gpaw")).
  dependsOn(core).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    name := "gpaw",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val quantumEspresso = (project in file("parsers/quantum-espresso")).
  dependsOn(core).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    name := "quantumEspresso",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)


lazy val lammps = (project in file("parsers/lammps")).
  dependsOn(core).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    name := "lammps",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val base = (project in file("base")).
  dependsOn(core).
  dependsOn(fhiAims).
  dependsOn(castep).
  dependsOn(cp2k).
  dependsOn(dlPoly).
  dependsOn(gaussian).
  dependsOn(exciting).
  dependsOn(gpaw).
  dependsOn(quantumEspresso).
  dependsOn(lammps).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= loggingLibs,
    name := "nomadBase"
  )

lazy val webservice = (project in file("webservice")).
  dependsOn(base).
  enablePlugins(BuildInfoPlugin).
  settings(gitVersionSettings: _*).
  settings(commonSettings: _*).
  enablePlugins(DockerPlugin).
  settings(
    libraryDependencies ++= sprayLibs,
    name := "nomadWebService",
    docker <<= (docker dependsOn assembly),
    dockerfile in docker := {
      val artifact = (assemblyOutputPath in assembly).value
      val artifactTargetPath = s"/app/${artifact.name}"
      new Dockerfile {
        from("ankitkariryaa/sbt-javac")
        expose(8081)
        add(artifact, artifactTargetPath)
        entryPoint("bash")
        //        entryPoint("java", "-jar", artifactTargetPath)
      }
    }
  ).
  settings(Revolver.settings: _*)

lazy val tool = (project in file("tool")).
  dependsOn(base).
  settings(commonSettings: _*).
  enablePlugins(DockerPlugin).
  settings(
    name := "nomadTool",
    docker <<= (docker dependsOn assembly),
    dockerfile in docker := {
      val artifact = (assemblyOutputPath in assembly).value
      val artifactTargetPath = s"/app/${artifact.name}"
      new Dockerfile {
        from("java:7")
        add(artifact, artifactTargetPath)
        entryPoint("bash")
//        entryPoint("java", "-jar", artifactTargetPath)
      }
    }
  ).
  settings(Revolver.settings: _*)

lazy val calculationparser = (project in file("calculation-parser-worker")).
  dependsOn(base).
  settings(commonSettings: _*).
  enablePlugins(DockerPlugin).
  settings(
    name := "nomadCalculationParserWorker",
    libraryDependencies += rabbitmqLib,
    docker <<= (docker dependsOn assembly),
    dockerfile in docker := {
      val artifact = (assemblyOutputPath in assembly).value
      val artifactTargetPath = s"/app/${artifact.name}"
      new Dockerfile {
        from("ankitkariryaa/java-pip")
        expose(8081)
        add(artifact, artifactTargetPath)
        //        entryPoint("bash")
        entryPoint("java", "-jar", artifactTargetPath)
      }
    }
  ).
  settings(Revolver.settings: _*)

lazy val normalizer = (project in file("normalizer-worker")).
  dependsOn(base).
  settings(commonSettings: _*).
  settings(
    name := "nomadNormalizerWorker",
    libraryDependencies += rabbitmqLib
  ).
  settings(Revolver.settings: _*)

lazy val treeparserinitializer = (project in file("tree-parser-initializer")).
  dependsOn(base).
  settings(commonSettings: _*).
  settings(
    name := "treeparserinitializer",
    libraryDependencies += rabbitmqLib
  ).
  settings(Revolver.settings: _*)

lazy val treeparser = (project in file("tree-parser-worker")).
  dependsOn(base).
  settings(commonSettings: _*).
  enablePlugins(DockerPlugin).
  settings(
    name := "nomadTreeParserWorker",
    libraryDependencies += rabbitmqLib,
    docker <<= (docker dependsOn assembly),
    dockerfile in docker := {
      val artifact = (assemblyOutputPath in assembly).value
      val artifactTargetPath = s"/app/${artifact.name}"
      new Dockerfile {
        from("ankitkariryaa/java-pip")
        expose(8081)
        add(artifact, artifactTargetPath)
//        entryPoint("bash")
        entryPoint("java", "-jar", artifactTargetPath)
      }
    }
  ).
  settings(Revolver.settings: _*)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= loggingLibs
  ).
  aggregate(
    core,
    fhiAims,  castep, cp2k, dlPoly, gaussian, exciting, gpaw, quantumEspresso, lammps,
    base, webservice, tool, calculationparser, normalizer, treeparser)
