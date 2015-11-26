package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import ucar.ma2.{IndexIterator => NIndexIterator}
import ucar.ma2.DataType
import ucar.ma2.ArrayString
import scala.collection.breakOut
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import java.io.Writer
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import org.json4s.CustomSerializer

object ParseEvent {
  /** builds an array of the given size looking up the type from the backend metaInfo
    */
  def buildArray(backend: Option[ParserBackendExternal], metaName: String, valuesShape: Seq[Long], flatValues: List[JValue]): NArray = {
    import org.json4s.DefaultFormats;
    implicit val formats = DefaultFormats

    var dtypeStr: String = ""
    if (!backend.isEmpty && !metaName.isEmpty) {
      backend.get.metaInfoEnv.metaInfoRecordForName(metaName) match {
        case Some(metaI) =>
          metaI.dtypeStr match {
            case Some(dt) =>
              dtypeStr = dt
            case None =>
              ()
          }
        case None =>
          ()
      }
    }
    if (dtypeStr.isEmpty) {
      val size = if (!valuesShape.isEmpty)
        valuesShape.foldLeft(1: Long)(_ * _)
      else
        flatValues.size.longValue
      if (size > 0) {
        dtypeStr = flatValues(0) match {
          case _: JInt =>
            "i64"
          case _: JDouble =>
            "f64"
          case _: JString =>
            "C"
          case _: JObject =>
            "D"
          case _ =>
              throw new GenericBackend.InternalErrorException(s"cannot recover dtypeStr for $metaName from ${flatValues(0)}")
        }
      }
    }
    val ishape: Array[Int] = valuesShape.map(_.intValue).toArray
    val newArr = dtypeStr match {
      case "i" | "i32" =>
        NArray.factory(DataType.INT, ishape)
      case "i64" =>
        NArray.factory(DataType.LONG, ishape)
      case "f" | "f64" =>
        NArray.factory(DataType.DOUBLE, ishape)
      case "f32" =>
        NArray.factory(DataType.FLOAT, ishape)
      case "b" =>
        NArray.factory(DataType.BYTE, ishape)
      case "B" =>
        NArray.factory(DataType.STRING, ishape)
      case "C" =>
        NArray.factory(DataType.STRING, ishape)
      case "D" =>
        NArray.factory(DataType.STRING, ishape)
      case _ =>
        throw new GenericBackend.InternalErrorException(s"unsupported dtypeStr $dtypeStr for $metaName")
    }
    val writer = dtypeStr match {
      case "i" | "i32" =>
        { (it: NIndexIterator, value: JValue) =>
          it.setIntNext(value.extract[Int]) }
      case "i64" =>
        { (it: NIndexIterator, value: JValue) =>
          it.setLongNext(value.extract[Long]) }
      case "f" | "f64" =>
        { (it: NIndexIterator, value: JValue) =>
          it.setDoubleNext(value.extract[Double]) }
      case "f32" =>
        { (it: NIndexIterator, value: JValue) =>
          it.setFloatNext(value.extract[Float]) }
      case "b" =>
        { (it: NIndexIterator, value: JValue) =>
          it.setByteNext(value.extract[Byte]) }
      case "B" =>
        { (it: NIndexIterator, value: JValue) =>
          it.setObjectNext(value.extract[String]) }
      case "C" =>
        { (it: NIndexIterator, value: JValue) =>
          it.setObjectNext(value.extract[String]) }
      case "D" =>
        { (it: NIndexIterator, value: JValue) =>
          value match {
            case obj: JObject =>
              it.setObjectNext(JsonUtils.prettyStr(obj))
            case _ =>
              throw new JsonUtils.InvalidValueError(
                fieldName = metaName,
                context = "",
                value = JsonUtils.prettyStr(value),
                expected = "json dictionary")
          }
        }
      case _ =>
        throw new GenericBackend.InternalErrorException(s"Unexpected dtypeStr $dtypeStr for $metaName")
    }
    var l = flatValues

    val iter = newArr.getIndexIterator()
    while (iter.hasNext() && !l.isEmpty) {
      l match {
        case h::t =>
          writer(iter, h)
          l = t
        case Nil =>
          ()
      }
    }
    newArr
  }


  /** converts back from json
    */
  def fromJValue(backend: Option[ParserBackendExternal], value: JValue)(implicit format: org.json4s.Formats): ParseEvent = {
    value match {
      case JObject(obj) => {
        var event: String = "";
        var metaName: String = "";
        var gIndex: Long = -1;
        var shape: Option[Seq[Long]] = None;
        var valuesShape: Seq[Long] = Seq();
        var offset: Option[Seq[Long]] = None;
        var flatValues: List[JValue] = Nil;
        var references: Map[String, Long] = Map();
        var mainFileUri: String = "";
        var parserInfo: JValue = JNothing;
        obj foreach {
          case JField("event", value) =>
            value match {
              case JString(s)       => event = s
              case JNothing | JNull => ()
              case _                => throw new JsonUtils.InvalidValueError(
                "event", "ParseEvent", JsonUtils.prettyStr(value), "a string")
            }
          case JField("metaName", value) =>
            value match {
              case JString(s)       => metaName = s
              case JNothing | JNull => ()
              case _                => throw new JsonUtils.InvalidValueError(
                "metaName", "ParseEvent", JsonUtils.prettyStr(value), "a string")
            }
          case JField("gIndex", value) =>
            value match {
              case JInt(i)          => gIndex = i.longValue
              case JDecimal(i)      => gIndex = i.longValue
              case JNothing | JNull => ()
              case _                => throw new JsonUtils.InvalidValueError(
                "gIndex", "NomadMetaInfo", JsonUtils.prettyStr(value), "a string")
            }
          case JField("shape", value) =>
            if (!value.toOption.isEmpty)
              shape = Some(value.extract[Seq[Long]])
          case JField("valuesShape", value) =>
            if (!value.toOption.isEmpty)
              valuesShape = value.extract[Seq[Long]]
          case JField("offset", value) =>
            if (!value.toOption.isEmpty)
              offset = Some(value.extract[Seq[Long]])
          case JField("flatValues", value) =>
            if (!value.toOption.isEmpty)
              value match {
                case JArray(arr) =>
                  flatValues = arr
                case JNothing | JNull =>
                  ()
                case _ =>
                  throw new JsonUtils.InvalidValueError(
                    "flatValues", "ParseEvent", JsonUtils.prettyStr(value), "an array")
              }
          case JField("references", value) =>
            if (!value.toOption.isEmpty)
              references = value.extract[Map[String, Long]]
          case JField("mainFileUri", value) =>
            value match {
              case JString(s)       => mainFileUri = s
              case JNothing | JNull => ()
              case _                => throw new JsonUtils.InvalidValueError(
                "mainFileUri", "ParseEvent", JsonUtils.prettyStr(value), "a string")
            }
          case JField("parserInfo", value) =>
            parserInfo = value
          case JField(field, value) =>
            throw new JsonUtils.UnexpectedValueError(
              "ParseEvent", field, "Unexpected field with value ${JsonUtils.prettyStr(value)}")
        }
        event match {
          case "startedParsingSession" =>
            StartedParsingSession(mainFileUri, parserInfo)
          case "finishedParsingSession" =>
            FinishedParsingSession(mainFileUri, parserInfo)
          case "setSectionInfo" =>
            SetSectionInfo(metaName, gIndex, references)
          case "closeSection" =>
            CloseSection(metaName, gIndex)
          case "addValue" =>
            AddValue(metaName, value, gIndex)
          case "addRealValue" =>
            value match {
              case JDouble(d) =>
                AddRealValue(metaName, d, gIndex)
              case JInt(i) =>
                AddRealValue(metaName, i.doubleValue, gIndex)
              case JDecimal(d) =>
                AddRealValue(metaName, d.doubleValue, gIndex)
              case _ =>
                throw new JsonUtils.InvalidValueError(
                  "value", "ParseEvent", JsonUtils.prettyStr(value), "addRealValue expects a real value")
            }
          case "addArray" =>
            shape match {
              case Some(dims) =>
                AddArray(metaName, dims, gIndex)
              case None =>
                throw new JsonUtils.InvalidValueError(
                  "shape", "ParseEvent", shape.mkString("[",",","]"), "addArray requires a shape")
            }
          case "setArrayValues" =>
            SetArrayValues(metaName, buildArray(backend, metaName, valuesShape, flatValues),
              offset, gIndex)
          case "addArrayValues" =>
            AddArrayValues(metaName, buildArray(backend, metaName, valuesShape, flatValues), gIndex)
          case "openSectionWithGIndex" =>
            OpenSectionWithGIndex(metaName, gIndex)
        }
      }
      case v =>
        throw new JsonUtils.InvalidValueError(
          "value", "ParseEvent", JsonUtils.prettyStr(value), "parse event should be an object")
    }
  }
}

/** Events of an external parser stream
  */
sealed abstract class ParseEvent {
  def eventName: String

  def toJValue: JValue
}

/** Started a parsing session
  */
final case class StartedParsingSession(
  mainFileUri: String, parserInfo: JValue
) extends ParseEvent {
  override def eventName: String = "startParsingSession"

  override def toJValue: JValue = {
    import org.json4s.JsonDSL._;
    ("event" -> eventName) ~
    ("mainFileUri" -> mainFileUri) ~
    ("parserInfo" -> parserInfo)
  }
}

/** finished a parsing session
  */
final case class FinishedParsingSession(
  mainFileUri: String, parserInfo: JValue
) extends ParseEvent {
  override def eventName: String = "finishedParsingSession"

  override def toJValue: JValue = {
    import org.json4s.JsonDSL._;
    ("event" -> eventName) ~
    ("mainFileUri" -> (if (mainFileUri.isEmpty) JNothing else JString(mainFileUri))) ~
    ("parserInfo" -> parserInfo)
  }
  }
}

/** sets info values of an open section.
  *
  * references should be references to gIndex of the root sections this section refers to.
  */
final case class SetSectionInfo(
  metaName: String, gIndex: Long, references: Map[String, Long]
) extends ParseEvent {
  override def eventName: String = "setSectionInfo"

  override def toJValue: JValue = {
    import org.json4s.JsonDSL._;
    ("event" -> eventName) ~
    ("gIndex" -> gIndex) ~
    ("references" -> references)
  }
}


/** closes a section
  *
  * after this no other value can be added to the section.
  * metaName is the name of the meta info, gIndex the index of the section
  */
final case class CloseSection(
  metaName: String, gIndex: Long
) extends ParseEvent {
  override def eventName: String = "closeSection"

  override def toJValue: JValue ={
    import org.json4s.JsonDSL._;
    ("event" -> eventName) ~
    ("gIndex" -> gIndex)
  }
}

/** Adds a json value corresponding to metaName.
  *
  * The value is added to the section the meta info metaName is in.
  * A gIndex of -1 means the latest section.
  */
final case class AddValue(
  metaName: String, value: JValue, gIndex: Long = -1
) extends ParseEvent {
  override def eventName: String = "addValue"

  override def toJValue: JValue = {
    import org.json4s.JsonDSL._;
    ("event" -> eventName) ~
    ("metaName" -> metaName) ~
    ("gIndex" -> (if (gIndex == -1) JNothing else JInt(gIndex))) ~
    ("value" -> value)
  }
}

/** Adds a floating point value corresponding to metaName.
  *
  * The value is added to the section the meta info metaName is in.
  * A gIndex of -1 means the latest section.
  */
final case class AddRealValue(
  metaName: String, value: Double, gIndex: Long = -1
) extends ParseEvent {
  override def eventName: String = "addRealValue"

  override def toJValue: JValue = {
    import org.json4s.JsonDSL._;
    ("event" -> eventName) ~
    ("metaName" -> metaName) ~
    ("gIndex" -> (if (gIndex == -1) JNothing else JInt(gIndex))) ~
    ("value" -> value)
  }
}

/** Adds a new array of the given size corresponding to metaName.
  *
  * The value is added to the section the meta info metaName is in.
  * A gIndex of -1 means the latest section.
  * The array is unitialized.
  */
final case class AddArray(
  metaName: String, shape: Seq[Long], gIndex: Long = -1
) extends ParseEvent {
  override def eventName = "addArrayValue"

  override def toJValue: JValue = {
    import org.json4s.JsonDSL._;
    ("event" -> eventName) ~
    ("metaName" -> metaName) ~
    ("gIndex" -> (if (gIndex == -1) JNothing else JInt(gIndex))) ~
    ("shape" -> shape)
  }
}

object SetArrayValues {
  class UnexpectedDtypeException(
    metaName: String,
    dtype: DataType
  ) extends Exception(s"Unexpected type $dtype in $metaName") { }


  def flatValues(metaName: String, values: NArray): JArray = {
    val dtype = values.getDataType()
    val extractor: NIndexIterator => JValue = (
      if (dtype.isFloatingPoint())
        { (it: NIndexIterator) =>
          JDouble(it.getDoubleNext()) }
      else if (dtype.isIntegral())
        { (it: NIndexIterator) =>
          JInt(it.getLongNext()) }
      else if (dtype.isString())
        { (it: NIndexIterator) =>
          JString(it.next().toString()) }
      else
        throw new UnexpectedDtypeException(metaName, dtype)
    )
    val it = values.getIndexIterator()

    val arr = Array.fill[JValue](values.getSize().intValue)(JNothing)
    var ii = 0
    while (it.hasNext()) {
      arr(ii) = extractor(it)
      ii += 1
    }
    JArray(arr.toList)
  }
}
/** Adds values to the last array added
  */
final case class SetArrayValues(
  metaName: String,
  values: NArray,
  offset: Option[Seq[Long]] = None,
  gIndex: Long = -1
) extends ParseEvent {
  override def eventName = "setArrayValues"

  override def toJValue: JValue = {
    import org.json4s.JsonDSL._;
    ("event" -> eventName) ~
    ("metaName" -> metaName) ~
    ("gIndex" -> (if (gIndex == -1) JNothing else JInt(gIndex))) ~
    ("offset" -> (offset match {
      case Some(off) => JArray(off.map{ (o: Long) => JInt(o) }(breakOut): List[JInt])
      case None => JNothing
    })) ~
    ("valuesShape" -> JArray(values.getShape().map{ (s: Int) => JInt(s) }(breakOut): List[JInt])) ~
    ("flatValues"  -> SetArrayValues.flatValues(metaName, values))
  }
}
  /** Adds an array value with the given array values
    */
final case class AddArrayValues(
  metaName: String, values: NArray, gIndex: Long = -1
) extends ParseEvent {
  override def eventName = "addArrayValues"

  override def toJValue: JValue = JObject(
    ("event" -> JString(eventName)) ::
    ("metaName" -> JString(metaName)) ::
    ("gIndex" -> (if (gIndex == -1) JNothing else JInt(gIndex))) ::
    ("valuesShape" -> JArray(values.getShape().map{ (i: Int) => JInt(i)}(breakOut): List[JInt])) ::
    ("flatValues"  -> SetArrayValues.flatValues(metaName, values)) :: Nil
  )
}

  /** Informs tha backend that a section with the given gIndex should be opened
    *
    * The index is assumed to be unused, it is an error to reopen an existing section.
    */
final case class OpenSectionWithGIndex(
  metaName: String, gIndex: Long
) extends ParseEvent {
  override def eventName = "openSectionWithGIndex"

  override def toJValue: JValue = {
    import org.json4s.JsonDSL._;
    ("event" -> eventName) ~
    ("metaName" -> metaName) ~
    ("gIndex" -> gIndex)
  }
}
