package eu.nomad_lab

import org.specs2.mutable.Specification
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

/** Specification (fixed tests) for MetaInfo serialization
  */
class JsonUtilsSpec extends Specification {

  //implicit val formats = DefaultFormats + new eu.nomad_lab.MetaInfoRecordSerializer
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

  val jVal = JsonUtils.parseStr("""
    {
        "name": "TestProperty1",
        "description": "a meta info property to test serialization to json",
        "superNames": []
    }""")


  "jsonNormalized 1" >> {
    JsonUtils.normalizedStr(jObj1) must_== jObj1Str
    JsonUtils.normalizedStr(jObj2) must_== jObj1Str
  }

  "jsonComplexity" >> {
    JsonUtils.jsonComplexity(jObj1) must_== 14
    JsonUtils.jsonComplexity(jVal) must_== 4
  }

}
