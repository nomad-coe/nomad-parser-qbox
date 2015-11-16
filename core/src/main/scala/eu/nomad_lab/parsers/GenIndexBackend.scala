package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import scala.collection.mutable
import eu.nomad_lab.meta.MetaInfoEnv
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

object GenIndexBackend {

}

/** Backend that generates unique indexes for sections
  *
  * Converts and external backend to an internal one
  */
class GenIndexBackend(val subParser: ParserBackendExternal) extends ParserBackendInternal {
  def metaInfoEnv: MetaInfoEnv = subParser.metaInfoEnv

  class OpenSectionUsageException(msg: String) extends Exception(msg) {}

  val lastIndex: mutable.Map[String, Long] = mutable.Map()

  /** returns the sections that are still open
    *
    * sections are identified by name of the meta info and their gIndex
    */
  def openSections(): Iterator[(String, Long)] = subParser.openSections()

  /** returns information on an open section (for debugging purposes)
    */
  def openSectionInfo(metaName: String, gIndex: Long): String = {
    subParser.openSectionInfo(metaName, gIndex)
  }

  /** opens a new section returning a new identifier for it
    */
  def openSection(metaName: String): Long = {
    val next: Long = lastIndex.getOrElse(metaName, 0: Long) + 1
    lastIndex.update(metaName, next)
    subParser.openSectionWithGIndex(metaName, next)
    next
  }

  /** sets info values of an open section.
    *
    * references should be references to oldGIndex of the root sections this section refers to.
    */
  def setSectionInfo(metaName: String, gIndex: Long, references: Map[String, Long]): Unit = {
    subParser.setSectionInfo(metaName, gIndex, references)
  }

  /** closes a section
    *
    * after this no other value can be added to the section.
    * metaName is the name of the meta info, gIndex the index of the section
    */
  def closeSection(metaName: String, gIndex: Long): Unit = {
    subParser.closeSection(metaName, gIndex)
  }

  /** Adds a repating value to the section the value is in
    *
    * metaName is the name of the meta info, it should have repating=true
    * meaning that there can be multiple values in the same section
    */
  def addValue(metaName: String, value: JValue, gIndex: Long = -1): Unit = {
    subParser.addValue(metaName, value, gIndex)
  }

  /** Adds a repeating floating point value
    */
  def addRealValue(metaName: String, value: Double, gIndex: Long = -1): Unit = {
    subParser.addRealValue(metaName, value, gIndex)
  }

  /** Adds a new array of the given size
    */
  def addArrayValue(metaName: String, shape: Seq[Long], gIndex: Long = -1): Unit = {
    subParser.addArrayValue(metaName, shape, gIndex)
  }

  /** Adds values to the last array added
    */
  def setArrayValues(metaName: String, values: NArray, offset: Option[Seq[Long]], gIndex: Long = -1): Unit = {
    subParser.setArrayValues(metaName, values, offset, gIndex)
  }
}
