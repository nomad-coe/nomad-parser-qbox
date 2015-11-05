package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import ucar.ma2.MAMath
import scala.collection.breakOut
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

object CachingBackend {

  /** represents a section within a section manager, and can cache some values
    */
  class CachingSection (
    val gIndex: Long,
    var references: Array[Long],
    simpleValues: Map[String, Seq[JValue]] = Map(),
    arrayValues: Map[String, Seq[NArray]] = Map(),
    subSections: Map[String, Seq[CachingSection]] = Map(),
    var storeInSuper: Boolean = false
  ) {
    val cachedSimpleValues: mutable.Map[String, ListBuffer[JValue]] =
      simpleValues.map { case (s, list) =>
        s -> ListBuffer(list: _*) }(breakOut)
    val cachedArrayValues: mutable.Map[String, ListBuffer[NArray]] =
      arrayValues.map { case (s, list) =>
        s -> ListBuffer(list: _*) }(breakOut)
    val cachedSubSections: mutable.Map[String, ListBuffer[CachingSection]] =
      subSections.map { case (s, list) =>
        s -> ListBuffer(list: _*) }(breakOut)

    /** Caches the json value passed in associating it with the given metaInfo
      */
    def addValue(metaInfo: MetaInfoRecord, value: JValue) {
      cachedSimpleValues.get(metaInfo.name) match {
        case Some(vals) =>
          vals.append(value)
        case None =>
          cachedSimpleValues += (metaInfo.name -> ListBuffer(value))
      }
    }

    /** Sets values on an array (the latest) of metaIfo that should already be cached here
      */
    def setArrayValues[T](metaInfo: MetaInfoRecord, values: NArray, offset: Option[Seq[Long]]) {
      cachedArrayValues.get(metaInfo.name) match {
        case Some(vals) =>
          val arr = vals.last
          val targetShape = values.getShape()
          val targetOffset = offset match {
            case Some(off) =>
              off.map(_.intValue).toArray
            case None =>
              Array.fill[Int](targetShape.length)(0)
          }
          val target: NArray = arr.sectionNoReduce(targetOffset, targetShape, Array.fill[Int](targetShape.length)(1))
          MAMath.copy(target, values)
        case None =>
          throw new GenericBackend.InvalidAssignementException(metaInfo, s"setArrayValue called on ${metaInfo.name} in section $gIndex before addArrayValue")
      }
    }

    /** Caches in this section the given array for the given metaInfo
      */
    def addArrayValues(metaInfo: MetaInfoRecord, values: NArray) {
        cachedArrayValues.get(metaInfo.name) match {
        case Some(vals) =>
          vals.append(values)
        case None =>
          cachedArrayValues += (metaInfo.name -> ListBuffer(values))
      }
    }

    /** Caches a subsection of metaInfo here
      */
    def addSubsection(metaInfo: MetaInfoRecord, value: CachingSection) {
      cachedSubSections.get(metaInfo.name) match {
        case Some(vals) =>
          vals.append(value)
        case None =>
          cachedSubSections += (metaInfo.name -> ListBuffer(value))
      }
    }

    /** Callback when a section is closed
      *
      * add an overridable callback?
      */
    def onClose(backend: CachingBackend, sectionManager: CachingSectionManager): Unit = { }
  }

  /** Managere for sections that can cache results
    */
  class CachingSectionManager(
    val metaInfo: MetaInfoRecord,
    val parentSectionNames: Array[String],
    val backend: CachingBackend,
    lastSectionGIndex0: Long = -1,
    openSections0: Map[Long, CachingSection] = Map()
  ) extends GenericBackend.SectionManager {

    val openSections = mutable.Map[Long, CachingSection]()
    openSections ++= openSections0.iterator

    private var _lastSectionGIndex = lastSectionGIndex0

    /** the last section opened
      */
    def lastSectionGIndex: Long = _lastSectionGIndex

    /** returns the gIndexes of the sections that are still open
      */
    def openSectionsGIndexes(): Iterator[Long] = openSections.keysIterator;

    /** sets info values of an open section.
      *
      * references should be references to gIndex of the root sections this section refers to.
      */
    def setSectionInfo(gIndex: Long, references: Map[String, Long]): Unit = {
      openSections(gIndex).references = parentSectionNames.map(references(_))
    }

    /** returns the gIndex of a newly opened section
      */
    def openSection(): Long = {
      _lastSectionGIndex += 1
      val newGIndex = _lastSectionGIndex
      openSections += (newGIndex -> new CachingSection(
        newGIndex,
        references = parentSectionNames.map{ parentName: String =>
          backend.sectionManagers.get(parentName) match {
            case Some(parentManager) =>
              parentManager.lastSectionGIndex
            case None =>
              -1
          }
        }))
      newGIndex
    }


    /** closes the given section
      */
    def closeSection(gIndex: Long) = {
      val toClose = openSections(gIndex)
      toClose.onClose(backend, this)
      if (toClose.storeInSuper) {
        for ((superName, superGIndex) <- parentSectionNames.zip(toClose.references)) {
          val superSect = backend.sectionManagers(superName).openSections(superGIndex)
          superSect.addSubsection(metaInfo, toClose)
        }
      }
      openSections -= gIndex
    }

    /** Information on an open section
      */
    def openSectionInfo(gIndex: Long): String = {
      openSections.get(gIndex) match {
        case Some(section) =>
          s"section ${metaInfo.name} (${parentSectionNames.zip(section.references).mkString("[",",","]")})"
        case None =>
          s"section $gIndex in ${metaInfo.name} is not open!!"
      }
    }

    /** Stores the given json value in the section given by gIndex
      */
    def addValue(valueMetaInfo: MetaInfoRecord, value: JValue, gIndex: Long): Unit = {
      val gI = if (gIndex == -1)
        lastSectionGIndex
      else
        gIndex
      openSections(gI).addValue(valueMetaInfo, value)
    }

    /** Sets values in valueMetaInfo in an already added array in the section given by gIndex
      */
    def setArrayValues(valueMetaInfo: MetaInfoRecord, value: NArray, offset: Option[Seq[Long]], gIndex: Long): Unit = {
      val gI = if (gIndex == -1)
        lastSectionGIndex
      else
        gIndex
      openSections(gI).setArrayValues(valueMetaInfo, value, offset)
    }

    /** adds and array for metaInfo to the section gIndex
      */
    def addArrayValues(valueMetaInfo: MetaInfoRecord, value: NArray, gIndex: Long): Unit = {
      val gI = if (gIndex == -1)
        lastSectionGIndex
      else
        gIndex
      openSections(gI).addArrayValues(valueMetaInfo, value)
    }
  }

  /** caching for integer values
    */
  class CachingMetaDataManager_i(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.MetaDataManager_i(metaInfo) {

    def dispatch_i(value: Long, gIndex: Long): Unit = {
      sectionManager.addValue(metaInfo, JInt(value), gIndex)
    }
  }

  /** caching for floating point values
    */
  class CachingMetaDataManager_f(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.MetaDataManager_f(metaInfo) {

    def dispatch_f(value: Double, gIndex: Long): Unit = {
      sectionManager.addValue(metaInfo, JDouble(value), gIndex)
    }
  }

  /** caching for byte arrays (blobs)
    */
  class CachingMetaDataManager_B64(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.MetaDataManager_B64(metaInfo) {

    def dispatch_B64(value: String, gIndex: Long): Unit = {
      sectionManager.addValue(metaInfo, JString(value), gIndex)
    }
  }

  /** caching for string values
    */
  class CachingMetaDataManager_C(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.MetaDataManager_C(metaInfo) {

    def dispatch_C(value: String, gIndex: Long): Unit = {
      sectionManager.addValue(metaInfo, JString(value), gIndex)
    }
  }

  /** caching for json dictionaries
    */
  class CachingMetaDataManager_D(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.MetaDataManager_D(metaInfo) {

    def dispatch_D(value: JObject, gIndex: Long): Unit = {
      sectionManager.addValue(metaInfo, value, gIndex)
    }
  }

  /** handling of arrays, adding them to the cache
    */
  trait CachingArray {
    def sectionManager: CachingSectionManager;
    def metaInfo: MetaInfoRecord;
    def createArray(shape: Seq[Long]): NArray;

    def addArrayValue(shape: Seq[Long], gIndex: Long): Unit = {
      addArrayValues(createArray(shape), gIndex)
    }

    def addArrayValues(values: NArray, gIndex: Long): Unit = {
      sectionManager.addArrayValues(metaInfo, values, gIndex)
    }

    def setArrayValues(values: NArray, offset: Option[Seq[Long]], gIndex: Long): Unit = {
      sectionManager.setArrayValues(metaInfo, values, offset, gIndex)
    }
  }

  /** Caching for arrays of ints
    */
  class CachingMetaDataManager_Ai32(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_i32(metaInfo) with CachingArray {
  }

  /** Caching for arrays of longs
    */
  class CachingMetaDataManager_Ai64(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_i64(metaInfo) with CachingArray {
  }

  /** Caching for arrays of floats
    */
  class CachingMetaDataManager_Af32(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_f32(metaInfo) with CachingArray {
  }

  /** Caching for arrays of doubles
    */
  class CachingMetaDataManager_Af64(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_f64(metaInfo) with CachingArray {
  }

  /** Caching for arrays of bytes
    */
  class CachingMetaDataManager_Ab(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_b(metaInfo) with CachingArray {
  }

  /** Caching for arrays of byte arrays (generic binary data, blobs)
    */
  class CachingMetaDataManager_AB64(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_B64(metaInfo) with CachingArray {
  }

  /** Caching for arrays of unicode strings
    */
  class CachingMetaDataManager_AC(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_C(metaInfo) with CachingArray {
  }

  /** Caching for arrays of json dictionary
    */
  class CachingMetaDataManager_AD(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_D(metaInfo) with CachingArray {
  }

  /** Some meta info is invalid
    */
  class InvalidMetaInfoException(
    metaInfo: MetaInfoRecord, msg: String
  ) extends Exception(s"${metaInfo.name} is invalid: $msg, metaInfo: ${JsonUtils.prettyStr(metaInfo.toJValue())}") {}

  /** Error when compiling the meta info data
    */
  class MetaCompilationException(
    metaInfo: MetaInfoRecord, msg: String
  ) extends Exception(s"Error while compiling meta info ${metaInfo.name}: $msg, metaInfo: ${JsonUtils.prettyStr(metaInfo.toJValue())}") {}

  /** manager for the given meta info
    */
  def cachingDataManager(metaInfo: MetaInfoRecord, sectionManager: CachingSectionManager): GenericBackend.MetaDataManager = {
    if (metaInfo.kindStr != "type_document_content")
      throw new MetaCompilationException(metaInfo, "caching data manager can be instantiated only for conrete data (kindStr = type_document_content)")
    val scalar = metaInfo.shape match {
      case Some(shape) =>
        if (shape.isEmpty)
          true
        else
          false
      case None =>
        throw new InvalidMetaInfoException(metaInfo, "concrete meta info should have a shape ([] for scalar values)")
    }
    val dtypeStr = metaInfo.dtypeStr match {
      case Some(s) =>
        s
      case None =>
        throw new InvalidMetaInfoException(metaInfo, "concrete meta info should have a specific dtypeStr")
    }
    if (scalar) {
      dtypeStr match {
        case "f" | "f64" | "f32" => new CachingMetaDataManager_f(metaInfo, sectionManager)
        case "i" | "i64" | "i32" => new CachingMetaDataManager_i(metaInfo, sectionManager)
        case "b"                 => new CachingMetaDataManager_i(metaInfo, sectionManager)
        case "B"                 => new CachingMetaDataManager_B64(metaInfo, sectionManager)
        case "C"                 => new CachingMetaDataManager_C(metaInfo, sectionManager)
        case "D"                 => new CachingMetaDataManager_D(metaInfo, sectionManager)
        case _ =>
          throw new InvalidMetaInfoException(metaInfo, "Unknown dtypeStr, known types: f,f32,f64,i,i32,i64,b,B,C,D")
      }
    } else {
      dtypeStr match {
        case "f" | "f64" => new CachingMetaDataManager_Af64(metaInfo, sectionManager)
        case "f32"       => new CachingMetaDataManager_Af32(metaInfo, sectionManager)
        case "i" | "i32" => new CachingMetaDataManager_Ai32(metaInfo, sectionManager)
        case "i64"       => new CachingMetaDataManager_Ai64(metaInfo, sectionManager)
        case "b"         => new CachingMetaDataManager_Ab(metaInfo, sectionManager)
        case "B"         => new CachingMetaDataManager_AB64(metaInfo, sectionManager)
        case "C"         => new CachingMetaDataManager_AC(metaInfo, sectionManager)
        case "D"         => new CachingMetaDataManager_AD(metaInfo, sectionManager)
        case _ =>
          throw new InvalidMetaInfoException(metaInfo, "Unknown dtypeStr, known types: f,f32,f64,i,i32,i64,b,B,C,D")
      }
    }
  }

}

/** Backend that caches values
  */
class CachingBackend(
  metaInfoEnv: MetaInfoEnv,
  val sectionManagers: Map[String, CachingBackend.CachingSectionManager],
  val metaDataManagers: Map[String, GenericBackend.MetaDataManager]
) extends GenericBackend(metaInfoEnv) {

}