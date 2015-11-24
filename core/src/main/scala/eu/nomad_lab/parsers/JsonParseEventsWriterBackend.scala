package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import ucar.ma2.{IndexIterator => NIndexIterator}
import ucar.ma2.DataType
import ucar.ma2.ArrayString
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import java.io.Writer
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

class JsonParseEventsWriterBackend(
  metaInfoEnv: MetaInfoEnv,
  val outF: Writer
) extends BaseParserBackend(metaInfoEnv) with ParserBackendExternal {
  var writeComma: Boolean = false


  def startedParsingSession(mainFileUri: String, parserInfo: JValue): Unit = {
    outF.write("""{
  "type": "nomad_parse_events_1_0",
  "mainFileUri": """)
    JsonUtils.dumpString(mainFileUri, outF)
    outF.write(""",
  "parserInfo": """)
    JsonUtils.prettyWriter(parserInfo, outF, 2)
    outF.write(""",
  "events": [""")
  }

  def finishedParsingSession(mainFileUri: String): Unit = {
    outF.write("""]
}""")
  }

  def writeOut(event: JValue): Unit = {
    if (writeComma)
      outF.write(", ")
    else
      writeComma = true
    JsonUtils.prettyWriter(event, outF, 2)
  }

  /** sets info values of an open section.
    *
    * references should be references to gIndex of the root sections this section refers to.
    */
  override def setSectionInfo(metaName: String, gIndex: Long, references: Map[String, Long]): Unit = {
    writeOut(SetSectionInfo(metaName, gIndex, references).toJValue)
  }

  /** closes a section
    *
    * after this no other value can be added to the section.
    * metaName is the name of the meta info, gIndex the index of the section
    */
  override def closeSection(metaName: String, gIndex: Long): Unit = {
    super.closeSection(metaName, gIndex)
    writeOut(CloseSection(metaName, gIndex).toJValue)
  }

  /** Adds a json value corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    */
  override def addValue(metaName: String, value: JValue, gIndex: Long = -1): Unit = {
    writeOut(AddValue(metaName, value, gIndex).toJValue)
  }

  /** Adds a floating point value corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    */
  override def addRealValue(metaName: String, value: Double, gIndex: Long = -1): Unit = {
    writeOut(AddRealValue(metaName, value, gIndex).toJValue)
  }

  /** Adds a new array value of the given size corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    * The array is unitialized.
    */
  override def addArray(metaName: String, shape: Seq[Long], gIndex: Long = -1): Unit = {
    writeOut(AddArray(metaName, shape, gIndex).toJValue)
  }

  def flatWriter(metaName: String, values: NArray, outF: Writer): Unit = {
    val dtype = values.getDataType()
    val writer: NIndexIterator => Unit = (
      if (dtype.isFloatingPoint())
        { (it: NIndexIterator) =>
          val el = it.getDoubleNext()
          outF.write(el.toString())
        }
      else if (dtype.isIntegral())
        { (it: NIndexIterator) =>
          val el = it.getLongNext()
          outF.write(el.toString())
        }
      else if (dtype.isString())
        { (it: NIndexIterator) =>
          val el = it.next().toString()
          outF.write(el)
        }
      else
        throw new SetArrayValues.UnexpectedDtypeException(metaName, dtype)
    )
    val it = values.getIndexIterator()
    if (it.hasNext()) {
      writer(it)
      while (it.hasNext()){
        writeOut(JString(", "))
        writer(it)
      }
    }
  }

  /** Adds values to the last array added
    */
  override def setArrayValues(
    metaName: String, values: NArray,
    offset: Option[Seq[Long]] = None,
    gIndex: Long = -1): Unit = {
    if (writeComma)
      outF.write(", ")
    else
      writeComma = true
    outF.write("""{
  "event": "setArrayValues",
  "metaName": """)
    JsonUtils.dumpString(metaName, outF)
    offset match {
      case Some(off) =>
        outF.write(s"""
  "offset": ${off.mkString("[",", ","]")}""")
      case None => ()
    }
    outF.write(s""",
  "gIndex": $gIndex""")
    outF.write(s""",
  "valuesShape": ${values.getShape().mkString("[",", ","]")},
  "flatValues": [""")
    flatWriter(metaName, values, outF)
    outF.write("""]
  }""")
  }
  
  /** Adds an array value with the given array values
    */
  override def addArrayValues(metaName: String, values: NArray, gIndex: Long = -1): Unit = {
    if (writeComma)
      outF.write(", ")
    else
      writeComma = true
    outF.write("""{
  "event": "addArrayValues",
  "metaName": """)
    JsonUtils.dumpString(metaName, outF)
    outF.write(s""",
  "gIndex": $gIndex""")
    outF.write(s""",
  "valuesShape": ${values.getShape().mkString("[",", ","]")},
  "flatValues": [""")
    flatWriter(metaName, values, outF)
    outF.write("""]
  }""")
  }

  /** Informs tha backend that a section with the given gIndex should be opened
    *
    * The index is assumed to be unused, it is an error to reopen an existing section.
    */
  override def openSectionWithGIndex(metaName: String, gIndex: Long): Unit = {
    super.openSectionWithGIndex(metaName, gIndex)
    writeOut(OpenSectionWithGIndex(metaName, gIndex).toJValue)
  }

}

