package eu.nomad_lab

import org.specs2._
import org.json4s.DefaultFormats
import org.json4s.native.Serialization
import org.json4s._
import org.json4s.native.JsonMethods._

/** Specification (fixed tests) for MetaInfo serialization
  */
class JsonUtilsSpec extends mutable.Specification {

  implicit val formats = DefaultFormats + new eu.nomad_lab.MetaInfoRecordSerializer

  "jsonCompact 1" >> {
    import org.json4s.JsonDSL._;
    val jObj1 =
      ("a" -> 45) ~
        ("b" -> 1.0) ~
        ("c" -> "z") ~
        ("d" -> Seq(5,4,2,4)) ~
        ("e" -> false) ~
        ("f" -> true) ~
        ("g" -> JNothing) ~
        ("h" -> JNull) ~
        ("i" -> math.Pi);

    val jObj1Str = """{"a":45,"b":1.0,"c":"z","d":[5,4,2,4],"e":false,"f":true,"h":null,"i":3.141592653589793}"""

    val jObj2 = JObject(jObj1.obj.reverse)

    val jVal = parse("""
    {
        "name": "TestProperty1",
        "description": "a meta info property to test serialization to json",
        "superNames": []
    }""")

    JsonUtils.jsonCompactStr(jObj1) must_== jObj1Str
    JsonUtils.jsonCompactStr(jObj2) must_== jObj1Str
  }

}
