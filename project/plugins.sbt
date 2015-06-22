addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")
//lazy val root = project.in(file(".")).dependsOn(jooqPlugin)
//lazy val jooqPlugin = file("jooq-sbt-plugin")

//resolvers += "sean8223 Releases" at "https://github.com/sean8223/repository/raw/master/releases"
//addSbtPlugin("sean8223" %% "jooq-sbt-plugin" % "1.5")
addSbtPlugin("sean8223" %% "jooq-sbt-plugin" % "1.6.1")
resolvers += "Flyway" at "http://flywaydb.org/repo"
addSbtPlugin("org.flywaydb" % "flyway-sbt" % "3.2.1")
