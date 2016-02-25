/** to build the first time you need to execute
  * jooq:codegen which depends on flywayMigrate to generate the jooq db code
  */
scalaVersion  := "2.11.7"

lazy val commonSettings = Seq(
  organization  := "eu.nomad-laboratory",
  version       := "0.1",
  scalaVersion  := "2.11.7",
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
  resolvers     += "netcdf releases" at "http://artifacts.unidata.ucar.edu/content/repositories/unidata-releases",
  fork in run   := true /*,
  fork in Test  := true // breaks test summary report */
)

// json4s 3.3 is being finalized

lazy val commonLibs = {
  val akkaV = "2.3.9"
  val sprayV = "1.3.3"
  val json4sNative  = "org.json4s"         %% "json4s-native"  % "3.2.11"
  val json4sJackson = "org.json4s"         %% "json4s-jackson" % "3.2.11"
  val re2j          = "com.google.re2j"     % "re2j"           % "1.0"
  val configLib     = "com.typesafe"        % "config"         % "1.2.1"
  val spec2         = "org.specs2"         %% "specs2-core"    % "2.3.11" % "test"
  val h2            = "com.h2database"      % "h2"             % "1.4.187"
  val scalacheck    = "org.scalacheck"     %% "scalacheck"     % "1.12.4" % "test"
  val scalalog      = "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
  val tika          = "org.apache.tika"     % "tika-core"       % "1.10"
  val commonsCompress = "org.apache.commons"  % "commons-compress" % "1.10"
  val xzForJava     = "org.tukaani"         % "xz"              % "1.5"
  val netcdf        = "edu.ucar"            % "netcdf4"         % "4.6.3"
  val fastring      = "com.dongxiguo"      %% "fastring"        % "0.2.4"
  val playJson      = "com.typesafe.play"  %% "play-json"       % "2.4.3"
  val kafka         = "org.apache.kafka"   %% "kafka"           % "0.9.0.0" exclude("log4j", "log4j") exclude("org.slf4j","slf4j-log4j12")
  val pegdown       = "org.pegdown"         % "pegdown"         % "1.6.0"
  val rabbitMQ      = "com.rabbitmq"        % "amqp-client"      %"3.6.0"
  val log4j2        = Seq(
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.5",
    "org.apache.logging.log4j" % "log4j-api"        % "2.5",
    "org.apache.logging.log4j" % "log4j-core"       % "2.5",
    "org.apache.logging.log4j" % "log4j-1.2-api"    % "2.5")
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    spec2,
    re2j,
    configLib,
    json4sNative,
    json4sJackson,
    h2,
    scalacheck,
    tika,
    commonsCompress,
    xzForJava,
    netcdf,
    fastring,
    kafka,
    pegdown,
    rabbitMQ,
    scalalog) ++ log4j2
}

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

// ## queuing lib
val kafkaLib         = "org.apache.kafka"   %% "kafka"           % "0.9.0.0" exclude("log4j", "log4j") exclude("org.slf4j","slf4j-log4j12")

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

lazy val jooqCommon = seq(jooqSettings:_*) ++ Seq(
  jooqVersion := "3.6.2",
  (codegen in JOOQ) <<= (codegen in JOOQ).dependsOn(flywayMigrate)
    //  jooqForceGen := true
)

val rdbBase : Seq[(String,String)] = Seq(
  "generator.database.includes" -> ".*",
  "generator.target.packageName" -> "eu.nomad_lab.rdb",
  "generator.generate.pojos" -> "false",
  "generator.generate.immutablePojos" -> "false",
  "generator.generate.deprecated" -> "false"
//  "generator.strategy.name" -> "qgame.jooq.DBGeneratorStrategy",
//  "generator.name" -> "qgame.jooq.DBGenerator",
//  "generator.database.outputSchemaToDefault" -> "true"
)

lazy val rdbUrl = settingKey[String]("url to the rdb used during building")

lazy val flywayH2 = Seq(
  flywayUrl := rdbUrl.value,
  flywayLocations := Seq( "classpath:rdb/sql/common") //, "classpath:rdb/sql/h2")
)

// jooq db description generation
lazy val jooqH2 = Seq(
  libraryDependencies +=  "com.h2database"      %   "h2"            % "1.4.187"           % "jooq",
  jooqOptions := Seq(
        "jdbc.driver" -> "org.h2.Driver",
        "jdbc.url" -> rdbUrl.value,
        //"jdbc.user" -> "sa",
        //"jdbc.password" -> "sa",
        "generator.database.name" -> "org.jooq.util.h2.H2Database",
        "generator.database.inputSchema" -> "PUBLIC"
  ),
  jooqOptions ++= rdbBase
)

/*lazy val h2Settings = {
  Seq( rdbUrl := {
    "jdbc:h2:file:" + ((resourceManaged in Compile).value / "localdb_h2")
  } ) ++ flywayH2 ++ jooqCommon ++ jooqH2
};*/

lazy val flywayPostgres = (
  flywayUrl := rdbUrl.value,
  flywayUser := "nomad_lab",
  flywayPassword := "pippo",
  flywayLocations := Seq(
    "classpath:rdb/sql/common",
    "classpath:rdb/sql/postgres" )
)

/*lazy val jooqPostgres = Seq(
  libraryDependencies += "org.postgresql"      %   "postgresql"    % "9.4-1201-jdbc41" % "jooq",
  multiplyJooqOptions := Map("rdb" -> Seq(
    "jdbc.driver" -> "org.postgres.Driver",
    "jdbc.url" -> rdbUrl.value,
    "jdbc.user" -> "nomad_lab",
    "jdbc.password" -> "pippo",
    "generator.database.name" -> "org.jooq.util.postgres.PostgresDatabase"
  ) ++ rdbBase ) );
 */

/*lazy val postgresSettings = { Seq( rdbUrl := "jdbc:postgres://localhost:5432/nomad_lab" )
  ++ flywayPostgres ++ jooqCommon ++ jooqPostgres };*/

// code generation step to before, so the api can be used in core
// the generated code can also be generated to a directory that is then checked in
// for example with:
// settings(jooqOutputDirectory := (baseDirectory.value / "../core/src/main/java").getCanonicalFile())
// this can be useful to have a separate project not compiled by default and then add the
// generated files to git, for example for DB like postgres that not every developer might have installed
lazy val cgen = (project in file("cgen")).
  settings(commonSettings: _*).
  settings(
//    libraryDependencies ++= commonLibs,
    name := "cgen"
  ).
  settings(flywaySettings: _*).
  ////settings(h2Settings: _*).
  settings(rdbUrl := {
    "jdbc:h2:file:" + ((resourceManaged in Compile).value / "localdb_h2")
  } ).
  settings(flywayH2: _*).
  settings(jooqCommon: _*).
  settings(jooqH2: _*)

  //settings(sbtavrohugger.SbtAvrohugger.specificAvroSettings)


lazy val core = (project in file("core")).
  dependsOn(cgen).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (
      configLib +:
        netcdf +:
        json4sNativeLib +:
        json4sJacksonLib +:
        pegdownLib +:
        tikaLib +:
        kafkaLib +:
        testLibs ++:
        compressionLibs ++:
        loggingLibs),
    name := "nomadCore",
    (unmanagedResourceDirectories in Compile) += (baseDirectory.value / "../nomad-meta-info/meta_info").getCanonicalFile(),
    (unmanagedResourceDirectories in Compile) += (baseDirectory.value / "../python-common/common").getCanonicalFile()
  )//.
  //settings(flywaySettings: _*).
  ////settings(h2Settings: _*).
  //settings(rdbUrl := {
  //  "jdbc:h2:file:" + ((resourceManaged in Compile).value / "localdb_h2")
  //} ).
  //settings(flywayH2: _*).
  //settings(jooqCommon: _*).
  //settings(jooqH2: _*)


lazy val fhiAims = (project in file("parsers/fhi-aims")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
         ),
    name := "fhiAims"
  ).
  settings(Revolver.settings: _*)

lazy val castep = (project in file("parsers/castep")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
         ),
    name := "castep",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val cp2k = (project in file("parsers/cp2k")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
    name := "cp2k",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val dlPoly = (project in file("parsers/dl-poly")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
    name := "dlPoly",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val exciting = (project in file("parsers/exciting")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
    name := "exciting",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val gaussian = (project in file("parsers/gaussian")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
    name := "gaussian",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val gpaw = (project in file("parsers/gpaw")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
    name := "gpaw",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)

lazy val quantumEspresso = (project in file("parsers/quantum-espresso")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
    name := "quantumEspresso",
    (unmanagedResourceDirectories in Compile) += baseDirectory.value / "parser"
  ).
  settings(Revolver.settings: _*)


lazy val lammps = (project in file("parsers/lammps")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
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
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= commonLibs,
    name := "nomadBase"
  )

lazy val webservice = (project in file("webservice")).
  dependsOn(base).
  settings(commonSettings: _*).
  enablePlugins(DockerPlugin).
  settings(
    libraryDependencies ++= (//commonLibs,
      sprayLibs ++:
      testLibs
      ),
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
    libraryDependencies ++= (//commonLibs,
        testLibs
    ),
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
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
    name := "nomadCalculationParserWorker",
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
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
    name := "nomadNormalizerWorker"
  ).
  settings(Revolver.settings: _*)

lazy val treeparserinitializer = (project in file("tree-parser-initializer")).
  dependsOn(base).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
    name := "treeparserinitializer"
  ).
  settings(Revolver.settings: _*)

lazy val treeparser = (project in file("tree-parser-worker")).
  dependsOn(base).
  settings(commonSettings: _*).
  enablePlugins(DockerPlugin).
  settings(
    libraryDependencies ++= (//commonLibs,
      testLibs
      ),
    name := "nomadTreeParserWorker",
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
  aggregate(cgen, core, fhiAims, base, webservice, tool, calculationparser, normalizer, treeparser)
