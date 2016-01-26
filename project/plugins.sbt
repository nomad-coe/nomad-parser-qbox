addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.2.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.1")

resolvers += "Flyway" at "http://flywaydb.org/repo"
addSbtPlugin("org.flywaydb" % "flyway-sbt" % "3.2.1")

lazy val jooqPlugin = file("jooq-sbt-plugin")
lazy val root = project.in(file(".")).dependsOn(jooqPlugin)
//resolvers += "sean8223 Releases" at "https://github.com/sean8223/repository/raw/master/releases"
//addSbtPlugin("sean8223" %% "jooq-sbt-plugin" % "1.6-SNAPSHOT")
//addSbtPlugin("sean8223" %% "jooq-sbt-plugin" % "1.6.1")
//addSbtPlugin("com.julianpeeters" % "sbt-avrohugger" % "0.6.1")
