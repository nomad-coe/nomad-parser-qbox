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
  mainFileUri: String
) extends ParseEvent {
  override def eventName: String = "finishedParsingSession"

  override def toJValue: JValue = {
    import org.json4s.JsonDSL._;
    ("event" -> eventName) ~
    ("mainFileUri" -> mainFileUri)
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

/** Adds a new array value of the given size corresponding to metaName.
  *
  * The value is added to the section the meta info metaName is in.
  * A gIndex of -1 means the latest section.
  * The array is unitialized.
  */
final case class AddArrayValue(
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
