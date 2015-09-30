package eu.nomad_lab

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._

class NomadMetaInfoServiceSpec extends Specification with Specs2RouteTest with NomadMetaInfoService {
  def actorRefFactory = system
  
  lazy val metaInfoCollection: MetaInfoCollection = {
    val classLoader: ClassLoader = getClass().getClassLoader();
    val filePath = classLoader.getResource("nomad_meta_info/main.nomadmetainfo.json").getFile()
    val resolver = new RelativeDependencyResolver
    SimpleMetaInfoEnv.fromFilePath(filePath, resolver)
  }

  "NomadMetaInfoService" should {

    "meta info version v0.1 info.json is valid" in {
      Get("/nomad_meta_info/version/v0.1/info.json") ~> myRoute ~> check {
        (JsonUtils.parseStr(responseAs[String]) \ "name").extract[String] must_== "v0.1"
      }
    }

    "leave GET requests to other paths unhandled" in {
      Get("/kermit") ~> myRoute ~> check {
        handled must beFalse
      }
    }

    "return a MethodNotAllowed error for PUT requests to meta info info.json" in {
      Put("/nomad_meta_info/version/v0.1/info.json") ~> sealRoute(myRoute) ~> check {
        status === MethodNotAllowed
        responseAs[String] === "HTTP method not allowed, supported methods: GET"
      }
    }
  }
}
