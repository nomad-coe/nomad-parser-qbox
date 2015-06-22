organization  := "eu.nomad-laboratory"

version       := "0.1"

scalaVersion  := "2.11.6"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

// json4s 3.3 is being finalized

libraryDependencies ++= {
  val akkaV = "2.3.9"
  val sprayV = "1.3.3"
  val json4sNative  = "org.json4s"         %% "json4s-native"  % "3.2.11"
  val json4sJackson = "org.json4s"         %% "json4s-jackson" % "3.2.11"
  val re2j          = "com.google.re2j"     % "re2j"           % "1.0"
  val configLib     = "com.typesafe"        % "config"         % "1.2.1"
  val spec2         = "org.specs2"         %% "specs2-core"    % "2.3.11" % "test"
  val h2 =   "com.h2database"      %   "h2"            % "1.4.187"

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
    h2
  )
}



// flyway db migration
seq(flywaySettings: _*)

val h2Url = "jdbc:h2:file:~/localdb.h2";
flywayUrl := h2Url
//flywayUrl := "jdbc:postgres://localhost:5432/nomad_lab"
//flywayUser := "nomad_lab"
//flywayPassword := "pippo"
flywayLocations := Seq( "classpath:rdb/sql/common")
// jooq db description generation

seq(jooqSettings:_*)

libraryDependencies ++= Seq(
 //"org.postgresql"      %   "postgresql"    % "9.4-1201-jdbc41" % "jooq",
  "com.h2database"      %   "h2"            % "1.4.187"           % "jooq"
 )

(codegen in JOOQ) <<= (codegen in JOOQ).dependsOn(flywayMigrate)

val rdbH2: Seq[(String,String)] = Seq(
  "jdbc.driver" -> "org.h2.Driver",
  "jdbc.url" -> h2Url,
  //"jdbc.user" -> "sa",
  //"jdbc.password" -> "sa",
  "generator.database.name" -> "org.jooq.util.h2.H2Database"
)

val rdbPostgres : Seq[(String,String)] = Seq(
  "jdbc.driver" -> "org.postgres.Driver",
  "jdbc.url" -> "jdbc:postgres://localhost:5432/nomad_lab",
  "jdbc.user" -> "nomad_lab",
  "jdbc.password" -> "pippo",
  "generator.database.name" -> "org.jooq.util.postgres.PostgresDatabase"
)

val rdb : Seq[(String,String)] = rdbH2 ++ Seq(
  "generator.database.includes" -> ".*",
  //"generator.database.inputSchema" -> "public",
  "generator.target.packageName" -> "eu.nomad_lab.rdb",
  "generator.generate.pojos" -> "false",
  "generator.generate.immutablePojos" -> "false"
//  "generator.strategy.name" -> "qgame.jooq.DBGeneratorStrategy",
//  "generator.name" -> "qgame.jooq.DBGenerator",
//  "generator.database.outputSchemaToDefault" -> "true"
)

jooqForceGen := true;

multiplyJooqOptions := Map("rdb" -> rdb)

//jooqVersion := "3.6.2"

Revolver.settings
