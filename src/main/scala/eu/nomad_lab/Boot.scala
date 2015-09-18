package eu.nomad_lab

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import com.typesafe.config.{Config, ConfigFactory}

object Boot extends App {
  val conf: Config = ConfigFactory.load()

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can", conf)

  // create and start our service actor
  val service = system.actorOf(Props[NomadMetaInfoActor], "demo-service")

  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "localhost", port = 8080)
}
