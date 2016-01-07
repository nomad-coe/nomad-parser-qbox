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

class ParseEventsEmitter(
  metaInfoEnv: MetaInfoEnv,
  val mainEventDigester: ParseEvent => Unit,
  val startStopDigester: ParseEvent => Unit
) extends BaseParserBackend(metaInfoEnv) with ParserBackendExternal {

  /** Internal callback called for all parse events
    */
  def emitEvent(event: ParseEvent): Unit = {
    mainEventDigester(event)
  }

  /** Started a parsing session
    */
  def startedParsingSession(
    mainFileUri: Option[String],
    parserInfo: JValue,
    parserStatus: Option[ParseResult.Value] = None,
    parserErrors: JValue = JNothing): Unit = {
    startStopDigester(StartedParsingSession(mainFileUri, parserInfo, parserStatus, parserErrors))
  }

  /** finished a parsing session
    */
  def finishedParsingSession(
    parserStatus: Option[ParseResult.Value],
    parserErrors: JValue = JNothing,
    mainFileUri: Option[String] = None,
    parserInfo: JValue = JNothing): Unit = {
    startStopDigester(FinishedParsingSession(parserStatus, parserErrors, mainFileUri, parserInfo))
  }

  /** sets info values of an open section.
    *
    * references should be references to gIndex of the root sections this section refers to.
    */
  def setSectionInfo(metaName: String, gIndex: Long, references: Map[String, Long]): Unit = {
    emitEvent(SetSectionInfo(metaName, gIndex, references))
  }

  /** closes a section
    *
    * after this no other value can be added to the section.
    * metaName is the name of the meta info, gIndex the index of the section
    */
  override def closeSection(metaName: String, gIndex: Long): Unit = {
    super.closeSection(metaName, gIndex)
    emitEvent(CloseSection(metaName, gIndex))
  }

  /** Adds a json value corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    */
  override def addValue(metaName: String, value: JValue, gIndex: Long = -1): Unit = {
    emitEvent(AddValue(metaName, value, gIndex))
  }

  /** Adds a floating point value corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    */
  override def addRealValue(metaName: String, value: Double, gIndex: Long = -1): Unit = {
    emitEvent(AddRealValue(metaName, value, gIndex))
  }

  /** Adds a new array value of the given size corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    * The array is unitialized.
    */
  override def addArray(metaName: String, shape: Seq[Long], gIndex: Long = -1): Unit = {
    emitEvent(AddArray(metaName, shape, gIndex))
  }

  /** Adds values to the last array added
    */
  override def setArrayValues(
    metaName: String,
    values: NArray,
    offset: Option[Seq[Long]] = None,
    gIndex: Long = -1
  ): Unit = {
    emitEvent(SetArrayValues(metaName, values, offset, gIndex))
  }

  /** Adds an array value with the given array values
    */
  override def addArrayValues(metaName: String, values: NArray, gIndex: Long = -1): Unit = {
    emitEvent(AddArrayValues(metaName, values, gIndex))
  }

  /** Informs tha backend that a section with the given gIndex should be opened
    *
    * The index is assumed to be unused, it is an error to reopen an existing section.
    */
  override def openSectionWithGIndex(metaName: String, gIndex: Long): Unit = {
    emitEvent(OpenSectionWithGIndex(metaName, gIndex))
  }
}
