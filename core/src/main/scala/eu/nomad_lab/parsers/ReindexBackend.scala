package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import scala.collection.mutable
import eu.nomad_lab.meta.MetaInfoEnv
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

object ReindexBackend {
  class PendingInfo (
    val references: mutable.Map[String, Long],
    var nPendingReferences: Int
  ) {
    /** adds a resolved reference and updates pendingRefs
      */
    def addReference(metaName: String, gIndex: Long): Int = {
      references += metaName -> gIndex
      nPendingReferences -= 1
      nPendingReferences
    }
  }

  class SectionMapper {
    // mapping newGIndex -> pendingInfo
    val pendingInfos = mutable.Map[Long, PendingInfo]()
    // mapping oldGIndex -> [(String, newGIndex)]
    val missingSections = mutable.Map[Long, mutable.ListBuffer[(String,Long)]]()
    // mapping newGIndex -> pendingInfo
    val sectionMap = mutable.Map[Long, Long]()
  }

  /** Internal error in the index mapping
    */
  class MappingException(msg: String) extends Exception(msg) {}

}
/** Backend that changes the indexes used and keeps the mapping
  *
  * Converts and internal backend to an external one
  */
class ReindexBackend( val subParser: ParserBackendInternal) extends ParserBackendExternal {
  def metaInfoEnv: MetaInfoEnv = subParser.metaInfoEnv

  class OpenSectionUsageException(msg: String) extends Exception(msg) {}

  val sectionMappers = mutable.Map[String, ReindexBackend.SectionMapper]()

  /** Started a parsing session
    */
  def startedParsingSession(mainFileUri: String, parserInfo: JValue): Unit = {
    val exc = if (!sectionMappers.isEmpty)
      new ParserBackendBase.InvalidCallSequenceException("startParsingSession called when is not empty (meaning open session)")
    else
      null
    subParser.startedParsingSession(mainFileUri, parserInfo)
    if (exc != null)
        throw exc
  }

  /** Finished a parsing session
    */
  def finishedParsingSession(mainFileUri: String, parserInfo: JValue): Unit = {
    subParser.finishedParsingSession(mainFileUri, parserInfo)
    def testOrException(): Exception = {
      sectionMappers.foreach { case (metaName, sectionMapper: ReindexBackend.SectionMapper) =>
        if (!sectionMapper.pendingInfos.isEmpty) {
          return new ParserBackendBase.InvalidCallSequenceException(s"finishedParsingSession with still pending sections in $metaName: ${sectionMapper.pendingInfos}")
        }
      }
      null
    }
    val exception = testOrException()
    sectionMappers.clear()
    if (exception != null)
      throw exception
  }

  /** returns the sections that are still open
    *
    * sections are identified by name of the meta info and their gIndex
    */
  def openSections(): Iterator[(String, Long)] = {
    subParser.openSections().map { case (metaName, gIndex) =>
      sectionMappers(metaName).sectionMap.find { case (oldId,newId) =>
        newId == gIndex } match {
        case Some((oldId, newId)) =>
          (metaName, oldId)
        case None =>
          throw new ReindexBackend.MappingException(s"open section in sub parsers references unknwon index $gIndex")
      }
    }
  }

  /** returns information on an open section (for debugging purposes)
    */
  def sectionInfo(metaName: String, gIndex: Long): String = {
    val subInfo = subParser.sectionInfo(metaName, gIndex)
    val extraInfo = sectionMappers.get(metaName) match {
      case Some(sectionMapper) =>
        sectionMapper.pendingInfos.get(gIndex) match {
          case Some(pendingInfo) =>
            s" with pending info resolvedReferences:${pendingInfo.references.mkString("[",",","]")} nPendingReferences:${pendingInfo.nPendingReferences}"
          case None =>
            ""
        }
      case None =>
        ""
    }
    subInfo + extraInfo
  }

  def getOrCreateMapper(metaName: String): ReindexBackend.SectionMapper = {
    sectionMappers.get(metaName) match {
      case Some(sectionMapper) =>
        sectionMapper
      case None =>
        val newMapper = new ReindexBackend.SectionMapper
        sectionMappers += (metaName -> newMapper)
        newMapper
    }
  }

  /** opens a new section that had an identifier oldGIndex
    */
  def openSectionWithGIndex(metaName: String, oldGIndex: Long): Unit = {
    val newGIndex = subParser.openSection(metaName)
    val mapper = getOrCreateMapper(metaName)
    mapper.sectionMap += (oldGIndex -> newGIndex)
    mapper.missingSections.get(oldGIndex) match {
      case Some(sectionsToDo) =>
        sectionsToDo.foreach { case (pendingMetaName, pendingNewGIndex) =>
          val pendingMapper: ReindexBackend.SectionMapper = sectionMappers(pendingMetaName)
          val pending: ReindexBackend.PendingInfo = pendingMapper.pendingInfos(pendingNewGIndex)
          if (pending.addReference(metaName, newGIndex) == 0) {
            subParser.setSectionInfo(pendingMetaName, pendingNewGIndex, references = pending.references.toMap)
            pendingMapper.pendingInfos -= pendingNewGIndex
          }
        }
        mapper.missingSections -= oldGIndex
      case None =>
        ()
    }
  }

  /** sets info values of an open section.
    *
    * references should be references to oldGIndex of the root sections this section refers to.
    */
  def setSectionInfo(metaName: String, oldGIndex: Long, references: Map[String, Long]): Unit = {
    val selfMapper = sectionMappers(metaName)
    val newGIndex = selfMapper.sectionMap(oldGIndex)
    val resolvedRefs = mutable.Map[String, Long]()
    var nPendingReferences = 0
    references.foreach { case (refMetaName, metaOldGIndex) =>
      val mapper = getOrCreateMapper(refMetaName)
      mapper.sectionMap.get(metaOldGIndex) match {
        case Some(newMetaGIndex) =>
          resolvedRefs += (refMetaName -> newMetaGIndex)
        case None =>
          nPendingReferences += 1
          mapper.missingSections.get(metaOldGIndex) match {
            case Some(missing) =>
              missing += (refMetaName -> newGIndex)
            case None =>
              val missing = mutable.ListBuffer[(String,Long)](refMetaName -> newGIndex)
              mapper.missingSections += metaOldGIndex -> missing
          }
      }
    }
    if (nPendingReferences == 0) {
      subParser.setSectionInfo(metaName, newGIndex, resolvedRefs.toMap)
    } else {
      selfMapper.pendingInfos += (newGIndex -> new ReindexBackend.PendingInfo(
        resolvedRefs, nPendingReferences))
    }
  }

  def toNewGIndex(metaName:String, oldGIndex: Long): Long = {
    if (oldGIndex == -1)
      -1
    else
      sectionMappers(metaName).sectionMap(oldGIndex)
  }

  /** closes a section
    *
    * after this no other value can be added to the section.
    * metaName is the name of the meta info, gIndex the index of the section
    */
  def closeSection(metaName: String, oldGIndex: Long): Unit = {
    subParser.closeSection(metaName, toNewGIndex(metaName, oldGIndex))
  }

  /** Adds a repating value to the section the value is in
    *
    * metaName is the name of the meta info, it should have repating=true
    * meaning that there can be multiple values in the same section
    */
  def addValue(metaName: String, value: JValue, oldGIndex: Long = -1): Unit = {
    subParser.addValue(metaName, value, toNewGIndex(metaName, oldGIndex))
  }

  /** Adds a repeating floating point value
    */
  def addRealValue(metaName: String, value: Double, oldGIndex: Long = -1): Unit = {
    subParser.addRealValue(metaName, value, toNewGIndex(metaName, oldGIndex))
  }

  /** Adds a new array of the given size
    */
  def addArray(metaName: String, shape: Seq[Long], oldGIndex: Long = -1): Unit = {
    subParser.addArray(metaName, shape, toNewGIndex(metaName, oldGIndex))
  }

  /** Adds values to the last array added
    */
  def setArrayValues(metaName: String, values: NArray, offset: Option[Seq[Long]], oldGIndex: Long = -1): Unit = {
    subParser.setArrayValues(metaName, values, offset, toNewGIndex(metaName, oldGIndex))
  }
}
