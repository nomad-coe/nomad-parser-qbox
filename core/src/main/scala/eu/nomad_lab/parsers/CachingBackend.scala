package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import ucar.ma2.MAMath
import scala.collection.breakOut
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

object CachingBackend {
    type SectionCallback = (CachingBackend, CachingSectionManager, Long, Option[CachingSection]) => Unit

  /** error while setting a value
    */
  class SetValueError(
    valueMetaInfo: MetaInfoRecord,
    gIndex: Long,
    openSections: mutable.Map[Long, CachingSection],
    msg: String,
    what: Throwable = null
  ) extends Exception (
    s"Error setting value for ${JsonUtils.normalizedStr(valueMetaInfo.toJValue())} in section with gIndex $gIndex ${if (!openSections.contains(gIndex)) "(section not open!)" else ""}: $msg",
    what
  )

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
    def addValue(metaInfo: MetaInfoRecord, value: JValue): Unit = {
      cachedSimpleValues.get(metaInfo.name) match {
        case Some(vals) =>
          vals.append(value)
        case None =>
          cachedSimpleValues += (metaInfo.name -> ListBuffer(value))
      }
    }

    /** Sets values on an array (the latest) of metaInfo that should already be cached here
      */
    def setArrayValues(metaInfo: MetaInfoRecord, values: NArray, offset: Option[Seq[Long]]): Unit = {
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
          throw new GenericBackend.InvalidAssignementException(metaInfo, s"setArrayValue called on ${metaInfo.name} in section $gIndex before addArray")
      }
    }

    /** Caches in this section the given array for the given metaInfo
      */
    def addArrayValues(metaInfo: MetaInfoRecord, values: NArray): Unit = {
        cachedArrayValues.get(metaInfo.name) match {
        case Some(vals) =>
          vals.append(values)
        case None =>
          cachedArrayValues += (metaInfo.name -> ListBuffer(values))
      }
    }

    /** Caches a subsection of metaInfo here
      */
    def addSubsection(metaInfo: MetaInfoRecord, value: CachingSection): Unit = {
      cachedSubSections.get(metaInfo.name) match {
        case Some(vals) =>
          vals.append(value)
        case None =>
          cachedSubSections += (metaInfo.name -> ListBuffer(value))
      }
    }
  }

  /** Manager for sections that can cache results
    */
  class CachingSectionManager(
    val metaInfo: MetaInfoRecord,
    val parentSectionNames: Array[String],
    val superBackend: Option[ParserBackendExternal] = None,
    val isCaching: Boolean = true,
    val storeInSuper: Boolean = false,
    onOpenCallbacks0: Seq[CachingBackend.SectionCallback] = Seq(),
    onCloseCallbacks0: Seq[CachingBackend.SectionCallback] = Seq(),
    lastSectionGIndex0: Long = -1,
    openSections0: Map[Long, CachingSection] = Map()
  ) extends GenericBackend.SectionManager {

    val onOpenCallbacks = mutable.ListBuffer[CachingBackend.SectionCallback](onOpenCallbacks0: _*)
    val onCloseCallbacks = mutable.ListBuffer[CachingBackend.SectionCallback](onCloseCallbacks0: _*)
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
      if (isCaching)
        openSections(gIndex).references = parentSectionNames.map(references(_))
    }

    /** returns the gIndex of a newly opened section
      */
    def openSection(gBackend: GenericBackend): Long = {
      val newGIndex = lastSectionGIndex + 1
      openSectionWithGIndex(gBackend, newGIndex)
      newGIndex
    }

    def openSectionWithGIndex(gBackend: GenericBackend, gIndex: Long): Unit = {
      _lastSectionGIndex = gIndex
      val backend = gBackend match {
        case b: CachingBackend => b
      }
      var sect: Option[CachingSection] = None
      if (isCaching) {
        val newSect = new CachingSection(
          gIndex,
          references = parentSectionNames.map{ parentName: String =>
            backend.sectionManagers.get(parentName) match {
              case Some(parentManager) =>
                parentManager.lastSectionGIndex
              case None =>
                -1
            }
          },
          storeInSuper = storeInSuper)
        openSections += (gIndex -> newSect)
        sect = Some(newSect)
      }
      superBackend match {
        case Some(backend) =>
          backend.openSectionWithGIndex(metaInfo.name, gIndex)
        case None => ()
      }
      for (callback <- onOpenCallbacks) {
        callback(backend, this, gIndex, sect)
      }
    }


    /** closes the given section
      */
    def closeSection(gBackend: GenericBackend, gIndex: Long) = {
      val toClose = openSections.get(gIndex)
      gBackend match {
        case backend: CachingBackend =>
          onClose(backend, gIndex, toClose)
          superBackend match {
            case Some(backend) =>
              backend.closeSection(metaInfo.name, gIndex)
            case None => ()
          }
          toClose match {
            case Some(sectionToClose) =>
              if (sectionToClose.storeInSuper) {
                for ((superName, superGIndex) <- parentSectionNames.zip(sectionToClose.references)) {
                  backend.sectionManagers(superName).openSections.get(superGIndex) match {
                    case Some(superSect) =>
                      superSect.addSubsection(metaInfo, sectionToClose)
                    case None =>
                      backend.storeToClosedSuper(superName, superGIndex, metaInfo, sectionToClose)
                  }
                }
                if (sectionToClose.references.isEmpty)
                  backend.addSubsection(metaInfo, sectionToClose)
              }
            case None =>
              if (isCaching)
                throw new CloseNonOpenSectionException(metaInfo, gIndex)
          }
      }
      openSections -= gIndex
    }

    /** callback on close (place to override to add specific actions)
      */
    def onClose(gBackend: CachingBackend, gIndex: Long, section: Option[CachingSection]): Unit = {
      onCloseCallbacks.foreach { (callback: CachingBackend.SectionCallback) =>
        callback(gBackend, this, gIndex, section)
      }
    }

    /** Information on an open section
      */
    def sectionInfo(gIndex: Long): String = {
      openSections.get(gIndex) match {
        case Some(section) =>
          s"open section ${metaInfo.name} (${parentSectionNames.zip(section.references).mkString("[",",","]")})"
        case None =>
          s"section $gIndex in ${metaInfo.name} is closed"
      }
    }

    /** Stores the given json value in the section given by gIndex
      */
    def addValue(valueMetaInfo: MetaInfoRecord, value: JValue, gIndex: Long): Unit = {
      try {
        val gI = if (gIndex == -1)
          lastSectionGIndex
        else
          gIndex
        openSections(gI).addValue(valueMetaInfo, value)
      } catch {
        case NonFatal(e) =>
          throw new SetValueError(valueMetaInfo, gIndex, openSections, "in addValue", e)
      }
    }

    /** Sets values in valueMetaInfo in an already added array in the section given by gIndex
      */
    def setArrayValues(valueMetaInfo: MetaInfoRecord, value: NArray, offset: Option[Seq[Long]], gIndex: Long): Unit = {
      try {
        val gI = if (gIndex == -1)
          lastSectionGIndex
        else
          gIndex
        openSections(gI).setArrayValues(valueMetaInfo, value, offset)
      } catch {
        case NonFatal(e) =>
          throw new SetValueError(valueMetaInfo, gIndex, openSections, "in setArrayValues", e)
      }
    }

    /** adds and array for metaInfo to the section gIndex
      */
    def addArrayValues(valueMetaInfo: MetaInfoRecord, value: NArray, gIndex: Long): Unit = {
      try {
        val gI = if (gIndex == -1)
          lastSectionGIndex
        else
          gIndex
        openSections(gI).addArrayValues(valueMetaInfo, value)
      } catch {
        case NonFatal(e) =>
          throw new SetValueError(valueMetaInfo, gIndex, openSections, "in addArrayValues", e)
      }
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

    def addArray(shape: Seq[Long], gIndex: Long): Unit = {
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

  /** Closing a section that is not open
    */
  class CloseNonOpenSectionException(
    metaInfo: MetaInfoRecord,
    gIndex: Long
  ) extends Exception(s"Close called on non open section ${metaInfo.name}, gIndex: $gIndex")

  /** manager for the given meta info
    */
  def cachingDataManager(metaInfo: MetaInfoRecord, sectionManager: CachingSectionManager, forward: Boolean = true): GenericBackend.MetaDataManager = {
    if (metaInfo.kindStr != "type_document_content" && metaInfo.kindStr != "type_dimension")
      throw new MetaCompilationException(metaInfo, "caching data manager can be instantiated only for conrete data (kindStr = type_document_content or type_dimension)")
    val scalar = metaInfo.shape match {
      case Some(shape) =>
        if (shape.isEmpty)
          true
        else
          false
      case None =>
        true
    }
    val dtypeStr = metaInfo.dtypeStr match {
      case Some(s) =>
        s
      case None =>
        throw new InvalidMetaInfoException(metaInfo, "concrete meta info should have a specific dtypeStr")
    }
    val cachingManager = if (scalar) {
      dtypeStr match {
        case "f" | "f64" | "f32" => new CachingMetaDataManager_f(metaInfo, sectionManager)
        case "i" | "i64" | "i32" | "r" => new CachingMetaDataManager_i(metaInfo, sectionManager)
        case "b"                 => new CachingMetaDataManager_i(metaInfo, sectionManager)
        case "B"                 => new CachingMetaDataManager_B64(metaInfo, sectionManager)
        case "C"                 => new CachingMetaDataManager_C(metaInfo, sectionManager)
        case "D"                 => new CachingMetaDataManager_D(metaInfo, sectionManager)
        case _ =>
          throw new InvalidMetaInfoException(metaInfo, "Unknown dtypeStr, known types: f,f32,f64,i,i32,i64,r,b,B,C,D")
      }
    } else {
      dtypeStr match {
        case "f" | "f64" => new CachingMetaDataManager_Af64(metaInfo, sectionManager)
        case "f32"       => new CachingMetaDataManager_Af32(metaInfo, sectionManager)
        case "i" | "i32" => new CachingMetaDataManager_Ai32(metaInfo, sectionManager)
        case "i64" | "r" => new CachingMetaDataManager_Ai64(metaInfo, sectionManager)
        case "b"         => new CachingMetaDataManager_Ab(metaInfo, sectionManager)
        case "B"         => new CachingMetaDataManager_AB64(metaInfo, sectionManager)
        case "C"         => new CachingMetaDataManager_AC(metaInfo, sectionManager)
        case "D"         => new CachingMetaDataManager_AD(metaInfo, sectionManager)
        case _ =>
          throw new InvalidMetaInfoException(metaInfo, "Unknown dtypeStr, known types: f,f32,f64,i,i32,i64,r,b,B,C,D")
      }
    }
    if (forward) {
      sectionManager.superBackend match {
        case Some(backend) =>
          new GenericBackend.ForwardDataManager(metaInfo, sectionManager, backend, Some(cachingManager))
        case None =>
          cachingManager
      }
    } else {
      cachingManager
    }
  }

  /** Enumeration to specify caching
    */
  object CachingLevel extends Enumeration {
    val Forward, Cache, CacheSubvalues, ForwardAndCache, Ignore = Value
  }

  /** Method to create a factory that creates caching sections
    *
    * Sections are stored in the super section by default for caching level or ForwardAndCache.
    * For Forward or CacheSubvalues the section is not stored in super (but a caching section is created),
    * whereas for Ignore no caching section is created at all.
    *
    * Open and close events of the section are sent to the superBackend with Forward and ForwardAndCache.
    * With other settings unless one sends open and close events through another route,
    * no contained value or section should be emitted to the superBackend (i.e all contained
    * values and sections should also be Cache, CachesSubvalues or Ignore.
    */
  def cachingSectionFactory(
    cachingLevelForMetaName: Map[String, CachingLevel.Value],
    defaultSectionCachingLevel: CachingLevel.Value = CachingLevel.Forward,
    superBackend: Option[ParserBackendExternal] = None,
    onOpenCallbacks: Map[String, Seq[CachingBackend.SectionCallback]] = Map(),
    onCloseCallbacks: Map[String, Seq[CachingBackend.SectionCallback]] = Map()
  ): (MetaInfoEnv, MetaInfoRecord, Array[String]) => CachingBackend.CachingSectionManager = {
    (metaEnv: MetaInfoEnv, metaInfo: MetaInfoRecord, superSectionNames: Array[String]) =>
    val callbacks = onCloseCallbacks.getOrElse(metaInfo.name, Seq())

    case class Flags(
      superBackend: Option[ParserBackendExternal],
      isCaching: Boolean,
      storeInSuper: Boolean)

    val flags = cachingLevelForMetaName.getOrElse(metaInfo.name, defaultSectionCachingLevel) match {
      case CachingLevel.Forward =>
        Flags(
          superBackend = superBackend,
          isCaching = true,
          storeInSuper = false)
      case CachingLevel.Cache =>
        Flags(
          superBackend = None,
          isCaching = true,
          storeInSuper = true)
      case CachingLevel.CacheSubvalues =>
        Flags(
          superBackend = None,
          isCaching = true,
          storeInSuper = false)
      case CachingLevel.ForwardAndCache =>
        Flags(
          superBackend = superBackend,
          isCaching = true,
          storeInSuper = true)
      case CachingLevel.Ignore =>
        Flags(
          superBackend = None,
          isCaching = false,
          storeInSuper = false)
    }
    new CachingSectionManager(metaInfo, superSectionNames,
      superBackend = flags.superBackend,
      isCaching = flags.isCaching,
      storeInSuper = flags.storeInSuper,
      onOpenCallbacks0 = onOpenCallbacks.getOrElse(metaInfo.name, Seq()),
      onCloseCallbacks0 = onCloseCallbacks.getOrElse(metaInfo.name, Seq()))
  }

  /** Method to create a factory that creates caching data
    *
    * Sections are stored in the super section by default for caching level Cache or ForwardAndCache.
    * For Forward the section is not stored in super (but a caching section is created),
    * whereas for Ignore no caching section is created at all.
    *
    * Open and close events of the section are sent to the superSection.superBackend
    * with Forward and ForwardAndCache.
    * With other settings unless one sends open and close events through another route,
    * no contained value or section should be emitted to the superBackend (i.e all contained
    * values and sections should also be Cache or ForwardAndCache.
    */
  def cachingDataFactory(
    cachingLevelForMetaName: Map[String, CachingLevel.Value],
    defaultDataCachingLevel: CachingLevel.Value = CachingLevel.Forward
  ): (MetaInfoEnv, MetaInfoRecord, CachingSectionManager) => GenericBackend.MetaDataManager = {
    (metaEnv: MetaInfoEnv, metaInfo: MetaInfoRecord, superSection: CachingSectionManager) =>
    cachingLevelForMetaName.getOrElse(metaInfo.name, defaultDataCachingLevel) match {
      case CachingLevel.Forward =>
        superSection.superBackend match {
          case Some(backend) =>
            new GenericBackend.ForwardDataManager(metaInfo, superSection, backend)
          case None =>
            new GenericBackend.DummyMetaDataManager(metaInfo, superSection)
        }
      case CachingLevel.Cache | CachingLevel.CacheSubvalues =>
        cachingDataManager(metaInfo, superSection, forward = false)
      case CachingLevel.ForwardAndCache =>
        cachingDataManager(metaInfo, superSection, forward = true)
      case CachingLevel.Ignore =>
        new GenericBackend.DummyMetaDataManager(metaInfo, superSection)
    }
  }

  /** Uses the given factory methods to instantiate the section and data managers
    */
  def instantiateManagers[T, U](
    metaEnv: MetaInfoEnv,
    sectionFactory: (MetaInfoEnv, MetaInfoRecord, Array[String]) => T,
    dataFactory: (MetaInfoEnv, MetaInfoRecord, T) => U
  ): Tuple2[Map[String, T], Map[String, U]] = {
    // sections
    val allNames: Set[String] = metaEnv.allNames.toSet
    val sectionManagers: Map[String, T] = allNames.flatMap{ (name:String) =>
      val metaInfo = metaEnv.metaInfoRecordForName(name, true, true).get
      if (metaInfo.kindStr == "type_section") {
        val superSectionNames = GenericBackend.firstSuperSections(metaEnv,name)
        Some(name -> sectionFactory(metaEnv, metaInfo, superSectionNames))
      } else {
        None
      }
    }(breakOut)
    // concrete data
    val metaDataManagers: Map[String, U] = allNames.flatMap{ (name:String) =>
      val metaInfo = metaEnv.metaInfoRecordForName(name, true, true).get
      if (metaInfo.kindStr == "type_document_content" || metaInfo.kindStr == "type_dimension") {
        val superSectionNames = GenericBackend.firstSuperSections(metaEnv,name)
        if (superSectionNames.size != 1)
          throw new InvalidMetaInfoException(metaInfo, s"multiple direct super sections: ${superSectionNames.mkString(", ")}")
        Some(name -> dataFactory(metaEnv, metaInfo, sectionManagers(superSectionNames(0))))
      } else {
        None
      }
    }(breakOut)

    (sectionManagers, metaDataManagers)
  }

  def apply(
    metaEnv: MetaInfoEnv,
    cachingLevelForMetaName: Map[String, CachingLevel.Value] = Map(),
    defaultSectionCachingLevel: CachingLevel.Value = CachingLevel.Forward,
    defaultDataCachingLevel: CachingLevel.Value = CachingLevel.ForwardAndCache,
    superBackend: Option[ParserBackendExternal] = None,
    onOpenCallbacks: Map[String, Seq[CachingBackend.SectionCallback]] = Map(),
    onCloseCallbacks: Map[String, Seq[CachingBackend.SectionCallback]] = Map()
  ): CachingBackend = {
    val sectionFactory = cachingSectionFactory(cachingLevelForMetaName, defaultSectionCachingLevel, superBackend, onOpenCallbacks, onCloseCallbacks)
    val dataFactory = cachingDataFactory(cachingLevelForMetaName, defaultDataCachingLevel)
    val (sectionManagers, metaDataManagers) = instantiateManagers(metaEnv, sectionFactory, dataFactory)
    new CachingBackend(metaEnv, sectionManagers, metaDataManagers)
  }

}

/** Backend that caches values
  */
class CachingBackend(
  metaInfoEnv: MetaInfoEnv,
  val sectionManagers: Map[String, CachingBackend.CachingSectionManager],
  val metaDataManagers: Map[String, GenericBackend.MetaDataManager],
  val superBackend: Option[ParserBackendExternal] = None
) extends GenericBackend(metaInfoEnv) with ParserBackendExternal with ParserBackendInternal {
  val cachedSubsections: mutable.Map[String, ListBuffer[CachingBackend.CachingSection]] = mutable.Map()

  /** Caches a subsection of metaInfo here
    */
  def addSubsection(metaInfo: MetaInfoRecord, value: CachingBackend.CachingSection): Unit = {
    cachedSubsections.get(metaInfo.name) match {
      case Some(vals) =>
        vals.append(value)
      case None =>
        cachedSubsections += (metaInfo.name -> ListBuffer(value))
    }
  }

  /** Started a parsing session
    */
  override def startedParsingSession(
    mainFileUri: Option[String],
    parserInfo: JValue,
    parserStatus: Option[ParseResult.Value] = None,
    parserErrors: JValue = JNothing
  ): Unit = {
    cachedSubsections.clear()
    super.startedParsingSession(mainFileUri, parserInfo, parserStatus, parserErrors)
  }

  /** Callback when a section should be stored in a closed super section
    */
  def storeToClosedSuper(superName: String, superGIndex: Long, metaInfo: MetaInfoRecord, toClose: CachingBackend.CachingSection): Unit = {
    logger.warn(s"Dropping section ${metaInfo.name} gIndex ${toClose.gIndex}, as it cannot be added to closed super section $superName gIndex: $superGIndex")
  }
}
