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

/** Base Parser Backend
  *
  * a backend that keeps track of the open sections, useful as basis
  * for other backends.
  */
abstract class BaseParserBackend(
  val metaInfoEnv: MetaInfoEnv
) extends ParserBackendBase {
  val _openSections = mutable.Set[(String, Long)]()

  /** returns the sections that are still open
    *
    * sections are identified by metaName and their gIndex
    */
  def openSections(): Iterator[(String, Long)] = _openSections.iterator

  /** returns information on an open section (for debugging purposes)
    */
  def openSectionInfo(metaName: String, gIndex: Long): String = {
    if (_openSections.contains(metaName -> gIndex))
      s"section $metaName, gIndex $gIndex"
    else
      s"*error* non open section $metaName, gIndex $gIndex"
  }

  /** closes a section
    *
    * after this no other value can be added to the section.
    * metaName is the name of the meta info, gIndex the index of the section
    */
  def closeSection(metaName: String, gIndex: Long): Unit = {
    _openSections -= (metaName -> gIndex)
  }

  /** Informs tha backend that a section with the given gIndex should be opened
    *
    * The index is assumed to be unused, it is an error to reopen an existing section.
    */
  def openSectionWithGIndex(metaName: String, gIndex: Long): Unit = {
    _openSections += (metaName -> gIndex)
  }

}
