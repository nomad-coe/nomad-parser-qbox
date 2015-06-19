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
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    spec2,
    re2j,
    configLib,
    json4sNative
  )
}



// flyway db migration
seq(flywaySettings: _*)


flywayUrl := "jdbc:h2:file:target/localdb"
//flywayUrl := "jdbc:postgres://localhost:5432/nomad_lab"
//flywayUser := "nomad_lab"
//flywayPassword := "pippo"
flywayLocations ++= Seq( "rdb/common")
// jooq db description generation

seq(jooqSettings:_*)

libraryDependencies ++= Seq(
  "org.postgresql"      %   "postgresql"    % "9.4-1201-jdbc41" % "jooq",
  "com.h2database"      %   "h2"            % "1.4.187"         % "jooq"
)

(codegen in JOOQ) <<= flywayMigrate

jooqOptions := Seq("jdbc.driver" -> "org.postgresql.Driver",
                    "jdbc.url" -> "jdbc:h2:file:target/localdb",
//                    "jdbc.url" -> "jdbc:postgres://localhost:5432/nomad_lab",
//                    "jdbc.user" -> "nomad_lab",
//                    "jdbc.password" -> "pippo",
//                    "generator.database.name" -> "org.jooq.util.mysql.MySQLDatabase",
//                    "generator.database.inputSchema" -> "fnord",
                    "generator.target.packageName" -> "eu.nomad-laboratory.rdb")

jooqVersion := "3.6.2"

Revolver.settings
