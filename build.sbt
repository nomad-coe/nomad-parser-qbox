/** to build the first time you need to execute
  * jooq:codegen which depends on flywayMigrate to generate the jooq db code
  */

lazy val commonSettings = Seq(
  organization  := "eu.nomad-laboratory",
  version       := "0.1",
  scalaVersion  := "2.11.6",
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
  resolvers     += "netcdf releases" at "http://artifacts.unidata.ucar.edu/content/repositories/unidata-releases",
  fork in run   := true /*,
  fork in Test  := true // breaks test summary report */
);

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
  val log4j2        = Seq(
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.3",
    "org.apache.logging.log4j" % "log4j-api"        % "2.3",
    "org.apache.logging.log4j" % "log4j-core"       % "2.3")

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
    h2,
    scalacheck,
    tika,
    commonsCompress,
    xzForJava,
    netcdf,
    fastring,
    scalalog) ++ log4j2
};

lazy val jooqCommon = seq(jooqSettings:_*) ++ Seq(
  jooqVersion := "3.6.2",
  (codegen in JOOQ) <<= (codegen in JOOQ).dependsOn(flywayMigrate)
    //  jooqForceGen := true
);

val rdbBase : Seq[(String,String)] = Seq(
  "generator.database.includes" -> ".*",
  "generator.target.packageName" -> "eu.nomad_lab.rdb",
  "generator.generate.pojos" -> "false",
  "generator.generate.immutablePojos" -> "false",
  "generator.generate.deprecated" -> "false"
//  "generator.strategy.name" -> "qgame.jooq.DBGeneratorStrategy",
//  "generator.name" -> "qgame.jooq.DBGenerator",
//  "generator.database.outputSchemaToDefault" -> "true"
);

lazy val rdbUrl = settingKey[String]("url to the rdb used during building");

lazy val flywayH2 = Seq(
  flywayUrl := rdbUrl.value,
  flywayLocations := Seq( "classpath:rdb/sql/common") //, "classpath:rdb/sql/h2")
);

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
);

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
);

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

lazy val core = (project in file("core")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= commonLibs,
    name := "nomadCore"
  ).
  settings(flywaySettings: _*).
  //settings(h2Settings: _*).
  settings(rdbUrl := {
    "jdbc:h2:file:" + ((resourceManaged in Compile).value / "localdb_h2")
  } ).
  settings(flywayH2: _*).
  settings(jooqCommon: _*).
  settings(jooqH2: _*)

lazy val base = (project in file("base")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= commonLibs,
    name := "nomadBase"
  )

lazy val webservice = (project in file("webservice")).
  dependsOn(base).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= commonLibs,
    name := "nomadWebService"
  ).
  settings(Revolver.settings: _*)

lazy val tool = (project in file("tool")).
  dependsOn(base).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= commonLibs,
    name := "nomadTool"
  ).
  settings(Revolver.settings: _*)
