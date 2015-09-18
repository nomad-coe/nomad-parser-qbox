package eu.nomad_lab

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class NomadMetaInfoActor extends Actor with NomadMetaInfoService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
}


// this trait defines our service behavior independently from the service actor
trait NomadMetaInfoService extends HttpService {
  def namesInVersion(version: String): Seq[String] = {
    Seq("toDo")
  }
  def baseUrl: String = "http://localhost/"

  val myRoute =
    pathPrefix("nomad_meta_info") {
      pathPrefix("version" / Segment) { version =>
        path("info.html") {
          get {
            respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
              complete {
                <html>
                <head>
                <title>{ "Nomad Meta Info Version " + version }</title>
                </head>
                <body>
                <title>{ "Nomad Meta Info Version " + version }</title>
                <ul>{
                  for (name <- namesInVersion(version)) yield {
                    <li><a href={ baseUrl + "nomad_meta_info/version/" + version + "/" + name }>{
                      name
                    }</a>
                    </li>
                  }
                }</ul>
                </body>
                </html>
              }
            }
          }
        } ~
        path("info.json") {
          get {
            respondWithMediaType(`application/json`) {
              complete {
                """
{
  "type": "nomad_meta_info_version",
  "name": "v0.1",
  "meta_info_record_names": [
    "name1",
    "name2"]
}
"""
              }
            }
          }
        }
              /*~
        path(StringValue) { name =>
          // .json
          // .html
        }*/
      } /*~
      pathPrefix("latest") {
        pathEnd {
          // name list
        } ~
        path("([^/]+)\\.json") { name =>
          // .json
          // .html
        }
         path("([^/]+)\\.json") { name =>
          // .json
          // .html
        }
      } ~
      pathPrefix("gid" / Segment) { version =>
        pathEnd {
          // name list
        } ~
        path(StringValue) { name =>
          // .json
          // .html
        }
      }*/
/*      get {
      } */
    }
}
