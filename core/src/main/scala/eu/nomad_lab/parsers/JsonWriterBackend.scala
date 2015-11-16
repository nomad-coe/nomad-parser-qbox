package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import ucar.ma2.DataType
import ucar.ma2.ArrayString
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

object JsonWriterBackend {
  class JsonWriterSectionManager(
    metaInfo: MetaInfoRecord,
    parentSectionNames: Array[String],
    lastSectionGIndex0: Long = -1,
    openSections0: Map[Long, CachingBackend.CachingSection] = Map(),
    standalone: Boolean = true
  ) extends CachingBackend.CachingSectionManager(
    metaInfo, parentSectionNames, lastSectionGIndex0, openSections0
  ) {
    override def onClose(gBackend: GenericBackend, gIndex: Long, section: CachingBackend.CachingSection): Unit = {
      val backend = gBackend match {
        case b: JsonWriterBackend => b
        case _ => throw new GenericBackend.InternalErrorException("expected JsonWriterBackend")
      }
      if (!standalone && section.references.size == 1) {
        backend.sectionManagers(parentSectionNames(0)).openSections.get(section.references(0)) match {
          case Some(openS) =>
            openS.addSubsection(metaInfo, section)
            return
          case None => ()
        }
      }
      backend.writeOut(section)
    }
  }
}

class JsonWriterBackend(
  metaInfoEnv: MetaInfoEnv,
  val sectionManagers: Map[String, CachingBackend.CachingSectionManager],
  val metaDataManagers: Map[String, GenericBackend.MetaDataManager],
  val outF: java.io.Writer,
  var writeHeader: Boolean = true
) extends GenericBackend(metaInfoEnv) {
  def writeOut(section: CachingBackend.CachingSection): Unit = {

  }

  def writeOutBase(section: CachingBackend.CachingSection, indent: Int): Unit = {

  }

}
