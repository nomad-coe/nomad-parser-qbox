package eu.nomad_lab.meta
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import org.json4s.CustomSerializer
import scala.collection.breakOut
import eu.nomad_lab.JsonUtils

/** Represents a piece of nomad meta info referring to other meta info by name.
  *
  * Can be interpreted within a context [[eu.nomad_lab.MetaInfoEnv]], but matches the
  * preferred way one describes it in a json file.
  */
case class MetaInfoRecord(
  val name: String,
  val kindStr: String,
  val description: String,
  val superNames:  Seq[String] = Seq(),
  val units:    Option[String] = None,
  val dtypeStr: Option[String] = None,
  val repeats: Option[Boolean] = None,
  val shape:  Option[Seq[Either[Long,String]]] = None,
  val gid:              String = "",
  val superGids:   Seq[String] = Seq(),
  val redundant: Option[Seq[String]] = None,
  val derived: Option[Boolean] = None,
  val referencedSections: Option[Seq[String]] = None,
  val otherKeys:  List[JField] = Nil) {

  /** returns a JValue (json) representation of the current record
    *
    * It is possible to control if the extra arguments are output
    * just like other fields, as a sub dictionary or not at all
    */
  def toJValue(extraArgs: Boolean = true, inlineExtraArgs: Boolean = true): JValue = {
    import org.json4s.JsonDSL._;
    val jShape = shape match {
      case None => JNothing
      case Some(s) =>
        val listShape: List[JValue] = s.map {
          case Left(i) => JInt(i)
          case Right(s) => JString(s)
        }(breakOut)
        JArray(listShape)
    }
    val baseObj = (
      ("name" -> name) ~
        ("gid" -> (if (gid.isEmpty) None else Some(gid))) ~
        ("kindStr" -> (if (kindStr == "type_document_content") None else Some(kindStr)))~
        ("description" -> description)~
        ("superNames" -> superNames)~
        ("superGids" -> (if (superGids.isEmpty) None else Some(superGids))) ~
        ("units"-> units)~
        ("dtypeStr" -> dtypeStr)~
        ("repeats" -> repeats)~
        ("shape" -> jShape)~
        ("redundant" -> redundant)~
        ("referencedSections" -> referencedSections)~
        ("derived" -> derived)
    );
    if (extraArgs) {
      if (inlineExtraArgs)
        JObject(baseObj.obj ++ otherKeys)
      else
        baseObj ~ ("extraArgs" -> JObject(otherKeys))
    } else {
      baseObj
    }
  }

  override def toString(): String = JsonUtils.prettyStr(toJValue())
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
    * B: byte array (blob)
    * C: unicode string
    * D: a json dictionary (currently not very efficient)
    *
    * Should probably be migrated to an Enumeration.
    */
  final val dtypes = Seq("f", "i", "f32", "i32", "f64", "i64", "b", "B", "C", "D", "r")
}

/** Json serialization to and deserialization support for MetaInfoRecord
  */
class MetaInfoRecordSerializer extends CustomSerializer[MetaInfoRecord](format => (
         {
           case JObject(obj) => {
             implicit val formats = format;
             var name: String = "";
             var gid: String = "";
             var kindStr: String = "type_document_content";
             var description: String = "";
             var superNames: Seq[String] = Seq();
             var superGids: Seq[String] = Seq();
             var units: Option[String] = None;
             var dtypeStr: Option[String] = None;
             var repeats: Option[Boolean] = None;
             var shape: Option[Seq[Either[Long,String]]] = None;
             var redundant: Option[Seq[String]] = None
             var derived: Option[Boolean] = None
             var referencedSections: Option[Seq[String]] = None
             var otherKeys: List[JField] = Nil;
             obj foreach {
               case JField("name", value) =>
                 value match {
                   case JString(s)       => name = s
                   case JNothing | JNull => ()
                   case _                => throw new JsonUtils.InvalidValueError(
                     "name", "NomadMetaInfo", JsonUtils.prettyStr(value), "a string")
                 }
               case JField("gid", value) =>
                 value match {
                   case JString(s)       => gid = s
                   case JNothing | JNull => ()
                   case _                => throw new JsonUtils.InvalidValueError(
                     "gid", "NomadMetaInfo", JsonUtils.prettyStr(value), "a string")
                 }
               case JField("kindStr", value) =>
                 value match {
                   case JString(s)       => kindStr = s
                   case JNothing | JNull => ()
                   case _                => throw new JsonUtils.InvalidValueError(
                     "kindString", "NomadMetaInfo", JsonUtils.prettyStr(value), MetaInfoRecord.dtypes.foldLeft("one of the following strings:"){ _ + " " + _ } )
                 }
               case JField("description", value) =>
                 value match {
                   case JString(s)       => description = s
                   case JArray(arr)      =>
                     val sb = new StringBuilder()
                     arr.foreach{
                       case JString(s) => sb ++= (s)
                       case JNothing   => ()
                       case _          => throw new JsonUtils.InvalidValueError(
                     "description", "NomadMetaInfo", JsonUtils.prettyStr(value), "either a string or an array of strings")
                     }
                     description = sb.toString
                   case JNothing | JNull => ()
                   case _                => throw new JsonUtils.InvalidValueError(
                     "description", "NomadMetaInfo", JsonUtils.prettyStr(value), "either a string or an array of strings")
                 }
               case JField("superNames", value) =>
                 if (!value.toOption.isEmpty)
                   superNames = value.extract[Seq[String]]
               case JField("referencedSections", value) =>
                 if (!value.toOption.isEmpty)
                   referencedSections = value.extract[Option[Seq[String]]]
               case JField("superGids", value) =>
                 if (!value.toOption.isEmpty)
                   superGids = value.extract[Seq[String]]
               case JField("units", value) =>
                 if (!value.toSome.isEmpty)
                   units = value.extract[Option[String]]
               case JField("dtypeStr", value) =>
                 if (!value.toSome.isEmpty)
                   dtypeStr = value.extract[Option[String]]
               case JField("repeats", value) =>
                 if (!value.toSome.isEmpty)
                   repeats = value.extract[Option[Boolean]]
               case JField("redundant", value) =>
                 if (!value.toSome.isEmpty)
                   redundant = value.extract[Option[Seq[String]]]
               case JField("derived", value) =>
                 if (!value.toSome.isEmpty)
                   derived = value.extract[Option[Boolean]]
               case JField("shape", value) =>
                 shape = value.extract[Option[Seq[Either[Long,String]]]]
               case JField(key, value) =>
                 if (!value.toSome.isEmpty)
                   otherKeys = JField(key, value) +: otherKeys
             }
             if (name.isEmpty) throw new JsonUtils.MissingFieldError("name", "NomadMetaInfo")
             if (description.isEmpty) throw new JsonUtils.MissingFieldError("description", "NomadMetaInfo")
             if (!dtypeStr.isEmpty && !(MetaInfoRecord.dtypes contains dtypeStr.get))
               throw new JsonUtils.InvalidValueError("dtypeStr", "NomadMetaInfo", dtypeStr.get, MetaInfoRecord.dtypes.foldLeft(""){ _ + " " + _ })
             if (!superGids.isEmpty && superNames.length != superGids.length)
               throw new JsonUtils.InvalidValueError("superGids", "NomadMetaInfo", superGids.mkString("[",", ","]"), s"incompatible length with superNames ${superNames.mkString("[",",","]")}")

             new MetaInfoRecord(name, kindStr, description, superNames, units,
               dtypeStr, repeats, shape, gid, superGids, redundant, derived,
               referencedSections, otherKeys)
           }
         },
         {
           case x: MetaInfoRecord => {
             x.toJValue()
           }
         }
       ))

/*class MetaInfoDbEnv(
  val name: String,
  val dbContext: () => jooq.DSLContext,
  val lazyLoad: Boolean) {
}*/
