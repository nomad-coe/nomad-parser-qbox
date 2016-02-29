// # Central place to add sbt plugins
// A central file is preferred, to clearly see extra dependencies like
// slf4j for sbt-git

// # re-start / re-stop commands to start an app in a separate process
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

// # creating docker images directly from sbt
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.2.0")

// # create a single jar for an application with all dependencies
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.1")

// # generate object with version numbers from git tags
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.5.0")

// // # git commands in sbt (to use the git sha in versions)
// // sbt git needs slf4j at build time, going with the simplest solution
// libraryDependencies ++= Seq(
//   "org.slf4j" % "slf4j-api" % "1.7.18",
//   "org.slf4j" % "slf4j-simple" % "1.7.18"
// )
// addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")

// # db migrations
resolvers += "Flyway" at "http://flywaydb.org/repo"
addSbtPlugin("org.flywaydb" % "flyway-sbt" % "3.2.1")

// # generation of objects mirroring the database schema
// we ship this plugin because downloading it as dependency is *slow*!
lazy val jooqPlugin = file("jooq-sbt-plugin")
lazy val root = project.in(file(".")).dependsOn(jooqPlugin)
//resolvers += "sean8223 Releases" at "https://github.com/sean8223/repository/raw/master/releases"
//addSbtPlugin("sean8223" %% "jooq-sbt-plugin" % "1.6-SNAPSHOT")
//addSbtPlugin("sean8223" %% "jooq-sbt-plugin" % "1.6.1")

// # generate scala case classes from avro definitions (disabled)
//addSbtPlugin("com.julianpeeters" % "sbt-avrohugger" % "0.6.1")
