package eu.nomad_lab
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import org.json4s.{DefaultFormats, CustomSerializer}

/** Represents a piece of nomad meta info referring to other meta info by name.
  *
  * Can be interpreted within a context [[eu.nomad_lab.MetaInfoEnv]], but matches the
  * preferred way one describes it in a json file.
  */
case class MetaInfoRecord(
  val name: String,
  val kindStr: String,
  val description: String,
  val superNames: Seq[String] = Seq(),
  val units: Option[String] = None,
  val dtypeStr: Option[String] = None,
  val repeats: Option[Boolean] = None,
  val shape: Option[Seq[Int]] = None,
  val otherKeys: Seq[JField] = Seq()) {
}

object MetaInfoRecord {
  /** valid dtypes
    * f: generic float (not specified, default dependent on settings)
    * i: generic integer (not specified, default dependent on settings)
    * f32: 32 bit floating point (single precision)
    * i32: signed 32 bit integer
    * f64: 64 bit floating point (double precision)
    * i64: 64 bit signed integer
    * b: byte
    * c: unicode character
    * B: byte array (blob)
    * C: unicode string
    */
  final val dtypes = Seq("f", "i", "f32", "i32", "u32", "f64", "i64", "u64", "b", "c", "B", "C")
}

/** Json serialization to and deserialization support for MetaInfoRecord
  */
class MetaInfoRecordSerializer extends CustomSerializer[MetaInfoRecord](format => (
         {
           case JObject(obj) => {
             implicit val formats = DefaultFormats;
             var name: String = "";
             var kindStr: String = "DocumentContentType";
             var description: String = "";
             var superNames: Seq[String] = Seq();
             var units: Option[String] = None;
             var dtypeStr: Option[String] = None;
             var repeats: Option[Boolean] = None;
             var shape: Option[Seq[Int]] = None;
             var otherKeys: Seq[JField] = Seq();
             obj foreach {
               case JField("name", value) =>
                 name = value.extract[String]
               case JField("kindStr", value) =>
                 kindStr = value.extract[String]
               case JField("description", value) =>
                 description = value.extract[String]
               case JField("superNames", value) =>
                 superNames = value.extract[Seq[String]]
               case JField("units", value) =>
                 units = value.extract[Option[String]]
               case JField("dtypeStr", value) =>
                 dtypeStr = value.extract[Option[String]]
               case JField("repeats", value) =>
                 repeats = value.extract[Option[Boolean]]
               case JField("shape", value) =>
                 shape = value.extract[Option[Seq[Int]]]
               case JField(key, value) =>
                 otherKeys = JField(key, value) +: otherKeys
             }
             if (name.isEmpty) throw new JsonUtils.MissingFieldError("name", "NomadMetaInfo")
             if (description.isEmpty) throw new JsonUtils.MissingFieldError("description", "NomadMetaInfo")
             if (!dtypeStr.isEmpty && !(MetaInfoRecord.dtypes contains dtypeStr.get))
               throw new JsonUtils.InvalidValueError("dtypeStr", "NomadMetaInfo", dtypeStr.get, MetaInfoRecord.dtypes.foldLeft(""){ _ + " " + _ })
             new MetaInfoRecord(name, kindStr, description, superNames, units,
               dtypeStr, repeats, shape, otherKeys)
           }
         },
         {
           case x: MetaInfoRecord => {
             import org.json4s.JsonDSL._;
             JObject(
               (("name", x.name) ~
                 ("kindStr", (if (x.kindStr == "DocumentContentType") None else Some(x.kindStr)))~
                 ("description", x.description)~
                 ("superNames", x.superNames)~
                 ("units", x.units)~
                 ("dtypeStr", x.dtypeStr)~
                 ("repeats", x.repeats)~
                 ("shape", x.shape)).obj ++
               x.otherKeys
             )
           }
         }
       ))


class MetaInfoEnv(
  val name: String,
  var gids: Map[String, String],
  var metaInfos: Map[String, MetaInfoRecord]) {

}

class MetaInfoDbEnv(
  val name: String,
//  val dbConnection: ActorRef,
  val lazyLoad: Boolean) {

}
