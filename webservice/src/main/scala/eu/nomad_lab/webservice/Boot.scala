package eu.nomad_lab.webservice

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import com.typesafe.config.{Config, ConfigFactory}
import eu.nomad_lab.LocalEnv

object Boot extends App {
  val conf: Config = ConfigFactory.load()

  LocalEnv.setup(conf)

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can", conf)

  // create and start our service actor
  val service = system.actorOf(Props[NomadMetaInfoActor], "nomad-frontend")

  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "localhost", port = 8081)
}
