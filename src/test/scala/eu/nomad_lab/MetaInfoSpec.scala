package eu.nomad_lab

import org.specs2.mutable.Specification
import org.json4s.DefaultFormats
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

/** Specification (fixed tests) for MetaInfo serialization
  */
class MetaInfoJsonSpec extends Specification {

  implicit val formats = DefaultFormats + new eu.nomad_lab.MetaInfoRecordSerializer

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
    mRecord.kindStr must_== "DocumentContentType"
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
    mRecord.kindStr must_== "DocumentContentType"
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
        "kindStr": "DocumentContentType"
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
    mRecord.kindStr must_== "DocumentContentType"
    mRecord.units must_== Some("pippo")
    mRecord.repeats must_== Some(false)
    mRecord.shape must_== Some(Seq())
    mRecord.otherKeys must beEmpty
  }

  "jsonRead null" >> {
    val mRecord = JsonSupport.readStr[MetaInfoRecord]("""
    {
        "name": "TestProperty3",
        "kindStr": "DocumentContentType"
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
    mRecord.kindStr must_== "DocumentContentType"
    mRecord.units must beNone
    mRecord.repeats must beNone
    mRecord.shape must beNone
    mRecord.otherKeys must beEmpty
  }

  "jsonRead extra" >> {
    val mRecord = JsonSupport.readStr[MetaInfoRecord]("""
    {
        "name": "TestProperty4",
        "kindStr": "DocumentContentType"
        "description": "a meta info property to test serialization to json",
        "superNames": ["TestProperty"],
        "extra": "nr1",
        "tt": 4
    }""")
    mRecord.name must_== "TestProperty4"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must_== Seq("TestProperty")
    mRecord.kindStr must_== "DocumentContentType"
    mRecord.units must beNone
    mRecord.repeats must beNone
    mRecord.shape must beNone
    mRecord.otherKeys must_==  JField("tt", JInt(4)) :: JField("extra", JString("nr1")) :: Nil
  }

  "simpleEnv test" >> {
    val jsonList = JsonUtils.parseStr("""
    [{
        "name": "TestProperty1",
        "kindStr": "DocumentContentType"
        "description": "a meta info property to test gids",
        "superNames": []
    },{
        "name": "TestProperty2",
        "kindStr": "DocumentContentType"
        "description": "a second meta info property to test gids",
        "superNames": ["TestProperty1"]
    },{
        "name": "TestProperty3",
        "gid": "dummyGid",
        "kindStr": "DocumentContentType"
        "description": "a third meta info property to test gids",
        "superNames": []
    },{
        "name": "TestProperty4",
        "kindStr": "DocumentContentType"
        "description": "a fourth meta info property to test gids",
        "superNames": ["TestProperty2","TestProperty3"]
    }]""").children
    val simpleEnv1 = SimpleMetaInfoEnv.fromJsonList(
      name = "test_env_1",
      source = JObject(JField("path",JString("<pseudo1>"))::Nil),
      jsonList =  jsonList,
      dependencyResolver = new NoDependencyResolver(),
      keepExistingGidsValues = true,
      ensureGids = true)

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
      source = JObject(JField("path",JString("<pseudo2>"))::Nil),
      jsonList =  jsonList,
      dependencyResolver = new NoDependencyResolver(),
      keepExistingGidsValues = false,
      ensureGids = true)
    val simpleEnv3 = SimpleMetaInfoEnv.fromJsonList(
      name = "test_env_3",
      source = JObject(JField("path",JString("<pseudo3>"))::Nil),
      jsonList =  (JArray(jsonList) \ "name").children.map {
        case JString(name) =>
          simpleEnv2.metaInfoRecordForName(name) match {
            case Some(r) => r.toJValue()
            case None => JNothing
          }
        case _ =>
          JNothing
      },
      dependencyResolver = new NoDependencyResolver(),
      keepExistingGidsValues = false,
      ensureGids = true)
  }

}
