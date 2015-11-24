package eu.nomad_lab.meta

import org.specs2.mutable.Specification
import org.json4s.DefaultFormats
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import java.nio.file.Paths
import scala.collection.mutable
import com.typesafe.scalalogging.StrictLogging
import eu.nomad_lab.JsonSupport
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.CompactSha

/** Specification (fixed tests) for MetaInfo serialization
  */
class MetaInfoJsonSpec extends Specification with StrictLogging {

  implicit val formats = JsonSupport.formats // DefaultFormats + new eu.nomad_lab.MetaInfoRecordSerializer

  "jsonExtract basic" >> {
    val jVal = JsonUtils.parseStr("""
    {
        "name": "TestProperty1",
        "description": "a meta info property to test serialization to json",
        "superNames": []
    }""")
    val mRecord = jVal.extract[MetaInfoRecord]
    mRecord.name must_== "TestProperty1"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must beEmpty
    mRecord.kindStr must_== "type_document_content"
    mRecord.units must beNone
    mRecord.repeats must beNone
    mRecord.shape must beNone
    mRecord.otherKeys must beEmpty
  }

  "jsonRead basic" >> {
    val mRecord = JsonSupport.readStr[MetaInfoRecord]("""
    {
        "name": "TestProperty1",
        "description": "a meta info property to test serialization to json",
        "superNames": []
    }""")
    mRecord.name must_== "TestProperty1"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must beEmpty
    mRecord.kindStr must_== "type_document_content"
    mRecord.units must beNone
    mRecord.repeats must beNone
    mRecord.shape must beNone
    mRecord.dtypeStr must beNone
    mRecord.otherKeys must beEmpty
  }

  "jsonRead extended" >> {
    val mRecord = JsonSupport.readStr[MetaInfoRecord]("""
    {
        "name": "TestProperty2",
        "kindStr": "type_document_content",
        "description": "a meta info property to test serialization to json",
        "superNames": [],
        "units": "pippo",
        "dtypeStr": "f64",
        "repeats": false,
        "shape": []
    }""")
    mRecord.name must_== "TestProperty2"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must beEmpty
    mRecord.kindStr must_== "type_document_content"
    mRecord.units must_== Some("pippo")
    mRecord.repeats must_== Some(false)
    mRecord.shape must_== Some(Seq())
    mRecord.otherKeys must beEmpty
  }

  "jsonRead null" >> {
    val mRecord = JsonSupport.readStr[MetaInfoRecord]("""
    {
        "name": "TestProperty3",
        "kindStr": "type_document_content"
        "description": "a meta info property to test serialization to json",
        "superNames": ["TestProperty"],
        "units": null,
        "dtypeStr": null,
        "repeats": null,
        "shape": null
    }""")
    mRecord.name must_== "TestProperty3"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must_== Seq("TestProperty")
    mRecord.kindStr must_== "type_document_content"
    mRecord.units must beNone
    mRecord.repeats must beNone
    mRecord.shape must beNone
    mRecord.otherKeys must beEmpty
  }

  "jsonRead extra" >> {
    val mRecord = JsonSupport.readStr[MetaInfoRecord]("""
    {
        "name": "TestProperty4",
        "kindStr": "type_document_content"
        "description": "a meta info property to test serialization to json",
        "superNames": ["TestProperty"],
        "extra": "nr1",
        "tt": 4
    }""")
    mRecord.name must_== "TestProperty4"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must_== Seq("TestProperty")
    mRecord.kindStr must_== "type_document_content"
    mRecord.units must beNone
    mRecord.repeats must beNone
    mRecord.shape must beNone
    mRecord.otherKeys must_==  JField("tt", JInt(4)) :: JField("extra", JString("nr1")) :: Nil
  }

  "simpleEnv test" >> {
    val jsonList = JsonUtils.parseStr("""
    [{
        "name": "TestProperty1",
        "description": "a meta info property to test gids",
        "superNames": []
    },{
        "name": "TestProperty2",
        "description": "a second meta info property to test gids",
        "superNames": ["TestProperty1"]
    },{
        "name": "TestProperty3",
        "kindStr": "type_document_content",
        "gid": "dummyGid",
        "description": "a third meta info property to test gids",
        "superNames": []
    },{
        "name": "TestProperty4",
        "kindStr": "type_abstract_document_content",
        "description": "a fourth meta info property to test gids",
        "superNames": ["TestProperty2","TestProperty3"]
    }]""") match {
      case arr @ JArray(_) => arr
      case _ => throw new Exception("expected an array")
    }
    val simpleEnv1 = SimpleMetaInfoEnv.fromJsonList(
      name = "test_env_1",
      description = "simpleEnv test environment 1",
      source = JObject(JField("path",JString("<pseudo1>"))::Nil),
      metaInfos =  jsonList,
      dependencies = JArray(Nil),
      dependencyResolver = new NoDependencyResolver(),
      keepExistingGidsValues = true,
      ensureGids = true,
      kind = MetaInfoEnv.Kind.File)

    JsonUtils.normalizedStr(jsonList(0)) must_== {
      JsonUtils.normalizedStr(simpleEnv1.metaInfoRecordForName("TestProperty1") match {
        case Some(r) => r.toJValue()
        case None => JNothing
      })
    }

    simpleEnv1.gidForName("TestProperty3").getOrElse("") must_== "dummyGid"

    val expectedGid: String = {
      val sha = CompactSha()
      JsonUtils.normalizedOutputStream(jsonList(0), sha.outputStream)
      sha.gidStr("p")
    }
    val storedGid: String = simpleEnv1.gidForName("TestProperty1").getOrElse("")

    expectedGid must_== storedGid

    val simpleEnv2 = SimpleMetaInfoEnv.fromJsonList(
      name = "test_env_2",
      description = "simpleEnv test environment 2",
      source = JObject(JField("path",JString("<pseudo2>"))::Nil),
      metaInfos =  jsonList,
      dependencies = JArray(Nil),
      dependencyResolver = new NoDependencyResolver(),
      keepExistingGidsValues = false,
      ensureGids = true,
      kind = MetaInfoEnv.Kind.File)

    simpleEnv2.gidForName("TestProperty1").getOrElse("") must_== simpleEnv1.gidForName("TestProperty1").getOrElse("")
    simpleEnv2.gidForName("TestProperty2").getOrElse("") must_== simpleEnv1.gidForName("TestProperty2").getOrElse("")
    simpleEnv2.gidForName("TestProperty3").getOrElse("dummyGid") must_!= "dummyGid"
    simpleEnv2.gidForName("TestProperty4").getOrElse("") must_!= simpleEnv1.gidForName("TestProperty4").getOrElse("")

    val simpleEnv3 = SimpleMetaInfoEnv.fromJsonList(
      name = "test_env_3",
      description = "simpleEnv test environment 3",
      source = JObject(JField("path",JString("/tmp/pseudo3"))::Nil),
      metaInfos = JArray((jsonList \ "name").children.map {
        case JString(name) =>
          simpleEnv2.metaInfoRecordForName(name) match {
            case Some(r) => r.toJValue()
            case None => JNothing
          }
        case _ =>
          JNothing
      }),
      dependencies = JArray(Nil),
      dependencyResolver = new NoDependencyResolver(),
      keepExistingGidsValues = false,
      ensureGids = true,
      kind = MetaInfoEnv.Kind.File)

    simpleEnv2.gidForName("TestProperty1").getOrElse("") must_== simpleEnv3.gidForName("TestProperty1").getOrElse("")
    simpleEnv2.gidForName("TestProperty2").getOrElse("") must_== simpleEnv3.gidForName("TestProperty2").getOrElse("")
    simpleEnv2.gidForName("TestProperty3").getOrElse("") must_== simpleEnv3.gidForName("TestProperty3").getOrElse("")
    simpleEnv2.gidForName("TestProperty4").getOrElse("") must_== simpleEnv3.gidForName("TestProperty4").getOrElse("")

    val simpleVersion = SimpleMetaInfoEnv.fromJsonList(
      name = "test_v1",
      description = "simpleEnv version",
      source = JObject(JField("path",JString("/tmp/vX"))::Nil),
      metaInfos = JArray(Nil),
      dependencies = JArray(JObject(JField("relativePath", JString("base")) :: Nil) :: Nil),
      dependencyResolver = new RelativeDependencyResolver(
        parentResolver = None,
        dependencies = mutable.Map[String, MetaInfoEnv]("/tmp/base" -> simpleEnv3)),
      keepExistingGidsValues = false,
      ensureGids = true,
      kind = MetaInfoEnv.Kind.Version)
    simpleVersion.gidForName("TestProperty1").getOrElse("") must_==  simpleEnv3.gidForName("TestProperty1").getOrElse("x")
  }

  "defaultEnv load" >> {
    val classLoader: ClassLoader = getClass().getClassLoader();
    val filePath = classLoader.getResource("nomad_meta_info/main.nomadmetainfo.json").getFile()
    val resolver = new RelativeDependencyResolver
    val mainEnv = SimpleMetaInfoEnv.fromFilePath(filePath, resolver)
    val version = new SimpleMetaInfoEnv(
      name = "last",
      description = "latest version, unlike all others this one is symbolic and will change in time",
      source = JObject( JField("path", JString(Paths.get(filePath).getParent().toString())) ),
      nameToGid = Map[String, String](),
      gidToName = Map[String, String](),
      metaInfosMap = Map[String, MetaInfoRecord](),
      dependencies = Seq(mainEnv),
      kind = MetaInfoEnv.Kind.Version)

    val vIt = version.versionsWithName("last")
    val v = vIt.next
    v must_== version
    val dp = version.metaInfoRecordForName("type_document_content").get
    dp.kindStr must_== "type_meta"
  }
}
