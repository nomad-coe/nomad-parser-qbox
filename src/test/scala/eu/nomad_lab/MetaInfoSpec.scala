package eu.nomad_lab

import org.specs2.mutable.Specification
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.{read, write}
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import org.json4s.native.JsonMethods.parse

/** Specification (fixed tests) for MetaInfo serialization
  */
class MetaInfoJsonSpec extends Specification {

  implicit val formats = DefaultFormats + new eu.nomad_lab.MetaInfoRecordSerializer

  "jsonExtract basic" >> {
    val jVal = parse("""
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
    val mRecord = read[MetaInfoRecord]("""
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
    val mRecord = read[MetaInfoRecord]("""
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
    val mRecord = read[MetaInfoRecord]("""
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
    val mRecord = read[MetaInfoRecord]("""
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

}
