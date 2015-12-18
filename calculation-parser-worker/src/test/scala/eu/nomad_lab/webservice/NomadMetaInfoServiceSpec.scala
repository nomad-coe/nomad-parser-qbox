package eu.nomad_lab.webservice

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._
import org.{json4s => jn}
import java.nio.file.Paths
import eu.nomad_lab.meta.MetaInfoCollection
import eu.nomad_lab.meta.RelativeDependencyResolver
import eu.nomad_lab.meta.SimpleMetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.JsonUtils

class NomadMetaInfoServiceSpec extends Specification with Specs2RouteTest with NomadMetaInfoService {
  def actorRefFactory = system
  
  val metaInfoCollection: MetaInfoCollection = {
    val classLoader: ClassLoader = getClass().getClassLoader();
    val filePath = classLoader.getResource("nomad_meta_info/main.nomadmetainfo.json").getFile()
    val resolver = new RelativeDependencyResolver
    val mainEnv = SimpleMetaInfoEnv.fromFilePath(filePath, resolver)
    new SimpleMetaInfoEnv(
      name = "last",
      description = "latest version, unlike all others this one is symbolic and will change in time",
      source = jn.JObject( jn.JField("path", jn.JString(Paths.get(filePath).getParent().toString())) ),
      nameToGid = Map[String, String](),
      gidToName = Map[String, String](),
      metaInfosMap = Map[String, MetaInfoRecord](),
      dependencies = Seq(mainEnv),
      kind = MetaInfoEnv.Kind.Version)
  }

  "NomadMetaInfoService" should {

    "meta info version of last version as expected" in {
      Get("/nmi/v/last/info.json") ~> myRoute ~> check {
        (JsonUtils.parseStr(responseAs[String]) \ "type").extract[String] must_== "nomad_meta_info_1_0"
      }
    }

    "leave GET requests to other paths unhandled" in {
      Get("/kermit") ~> myRoute ~> check {
        handled must beFalse
      }
    }

    "return a MethodNotAllowed error for PUT requests to meta info info.json" in {
      Put("/nmi/v/last/info.json") ~> sealRoute(myRoute) ~> check {
        status === MethodNotAllowed
        responseAs[String] === "HTTP method not allowed, supported methods: GET"
      }
    }
  }
}
