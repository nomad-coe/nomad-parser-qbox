package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import ucar.ma2.{Index => NIndex}
import ucar.ma2.MAMath
import ucar.ma2.DataType
import ucar.ma2.ArrayString
import scala.collection.breakOut
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import eu.nomad_lab.Base64
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.MetaInfoEnv
import eu.nomad_lab.MetaInfoRecord
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
//import scala.reflect.ClassTag
//import scala.reflect.classTag

/** prefiltering on the ancillary files performed
  *
  * This is to make it possible to avoid extracting all files from an archive
  */
object AncillaryFilesPrefilter extends Enumeration {
  type AncillaryFilesPrefilter = Value
  val WholeUpload, WholeSubtree, SubtreeDepth1, SameLevelOnly, MainFileOnly = Value
}

object Trilean extends Enumeration {
  type Trilean = Value
  val True, Maybe, False = Value
}

/** Describes an object able identify files it can parse and create a parser them
  *
  * all functions are expected to be reentrant and threadsafe
  */
trait ParserGenerator {
  /** description of the data that can be extracted by this parser
    */
  def parseableMetaInfo: MetaInfoEnv

  /** parser information: name, description,...
    */
  def parserInfo: JObject

  /** mime types to be checked as main files */
  def mainFileTypes: Seq[String]

  /** function that should decide if this main file can be parsed by this parser
    * looking at the first 1024 bytes of it
    */
  def isMainFile(filePath: String, bytePrefix: Array[Byte], stringPrefix: Option[String]): Trilean.Value

  /** returns an optimized parser that performs the actual parsing
    * 
    * The parser can excludes all the meta infos with names listed in the exclude
    * argument and those derived by them, then it adds all these explicitly
    * included meta infos and all their parents.
    *
    * All optimization are optional there is no guarantee that the resulting
    * parser really skips some data.
    */
  def optimizedParser(include: Seq[String], exclude: Seq[String]): OptimizedParser
}

/** Possible parse results
  *
  * * ParseSuccess means that the parser could sucessfully interpret the parsed file,
  *   not that the calculation is valid in any sense
  * * ParseWithWarning means that the parser could parse the file but there were
  *   parsing issues that might invalidate the data
  * * ParseSkipped means that the parser did not recognize the main file as a file
  *   it should parse
  * * Parse Failure meant that the parser did recognize the main file, but did fail
  *   to parse some of the data
  */
object ParseResult extends Enumeration {
  type ParseResult = Value
  val ParseFailure, ParseSkipped, ParseWithWarnings, ParseSuccess = Value
}

/** Parser that actually parses a main file
  * 
  * (and possibly several ancillary files) this is *not* threadsafe
  */
trait OptimizedParser {
  /** First rough filtering of the ancillary files that should be available for the parser
    */
  def ancillaryFilesPrefilter: AncillaryFilesPrefilter.Value

  /** Ancillary file filtering (only path based)
    */
  def isAncillaryFilePathForMainFilePath(mainFilePath: String, ancillaryFile: String): Boolean

  /** reference to the parser generator
    */
  def parserGenerator: ParserGenerator

  /** parses the file at the given path, calling the backend with the parser events
    */ 
  def parse(mainFilePath: String, backend: ParserBackend): ParseResult.ParseResult
}

/**Callbacks that are called by a streaming parser
  *
  * methods that should store or evaluate the data extracted by the parser
  */
trait ParserBackend {
  /** The metaInfoEnv this parser was optimized for
    */
  def metaInfoEnv: MetaInfoEnv;

  /** returns the sections that are still open
    *
    * sections are identified by metaName and their gIndex
    */
  def openSections(): Iterator[(String, Long)];

  /** returns information on an open section (for debugging purposes)
    */
  def openSectionInfo(metaName: String, gIndex: Long): String;

  /** opens a new section.
    */
  def openSection(metaName: String): Long;

  /** sets info values of an open section.
    *
    * references should be references to gIndex of the root sections this section refers to.
    */
  def setSectionInfo(metaName: String, gIndex: Long, references: Map[String, Long]);

  /** closes a section
    *
    * after this no other value can be added to the section.
    * metaName is the name of the meta info, gIndex the index of the section
    */
  def closeSection(metaName: String, gIndex: Long): Unit;

  /** Adds a json value corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    */
  def addValue(metaName: String, value: JValue, gIndex: Long = -1): Unit;

  /** Adds a floating point value corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    */
  def addRealValue(metaName: String, value: Double, gIndex: Long = -1): Unit;

  /** Adds a new array value of the given size corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    * The array is unitialized.
    */
  def addArrayValue(metaName: String, shape: Seq[Long], gIndex: Long = -1): Unit;

  /** Adds values to the last array added
    */
  def setArrayValues(
    metaName: String, values: NArray,
    offset: Option[Seq[Long]] = None,
    gIndex: Long = -1): Unit;
  
  /** Adds an array value with the given array values
    */
  def addArrayValues(metaName: String, values: NArray, gIndex: Long = -1): Unit = {
    addArrayValue(metaName, values.getShape().map(_.toLong).toSeq);
    setArrayValues(metaName, values);
  }
}

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

}
/** backend that changes the indexes used and keeps the mapping
  */
class ReindexBackend( val subParser: ParserBackend) extends ParserBackend {
  def metaInfoEnv: MetaInfoEnv = subParser.metaInfoEnv

  class OpenSectionUsageException(msg: String) extends Exception(msg) {}

  val sectionMappers = mutable.Map[String, ReindexBackend.SectionMapper]()

  /** returns the sections that are still open
    *
    * sections are identified by name of the meta info and their gIndex
    */
  def openSections(): Iterator[(String, Long)] = subParser.openSections()

  /** returns information on an open section (for debugging purposes)
    */
  def openSectionInfo(metaName: String, gIndex: Long): String = {
    val subInfo = subParser.openSectionInfo(metaName, gIndex)
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

  /** opens a new section, do not use, use openSectionWithOldGIndex
    */
  def openSection(metaName: String): Long = {
    throw new OpenSectionUsageException("cannot call openSection directly when reindexing, because then the mapping for references is unclear")
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
    * 
    * return newGIndex? might be confusing as it should not be used
    */
  def openSectionWithOldGIndex(metaName: String, oldGIndex: Long): Unit = {
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
  def addArrayValue(metaName: String, shape: Seq[Long], oldGIndex: Long = -1): Unit = {
    subParser.addArrayValue(metaName, shape, toNewGIndex(metaName, oldGIndex))
  }

  /** Adds values to the last array added
    */
  def setArrayValues(metaName: String, values: NArray, offset: Option[Seq[Long]], oldGIndex: Long = -1): Unit = {
    subParser.setArrayValues(metaName, values, offset, toNewGIndex(metaName, oldGIndex))
  }
}

/** A generic backend that can support direct storing, or caching in the Section
  *
  * Not supporting both might be slightly faster, but considered not worth the duplication
  * for now.
  */
object GenericBackend {

  class MissingSectionException(
    sectionName: String, gIndex: Long, msg: String, what: Throwable = null
  ) extends Exception(s"Missing section $sectionName with gIndex $gIndex, $msg", what) { }

  
  /** the backend was called in an invalid way given the metadata known
    */
  class InvalidAssignementException(
    metaInfo: MetaInfoRecord,
    msg:String
  ) extends Exception(s"Invalid assignement for meta data ${metaInfo.name}: $msg") { }

  /** root class for managers of sections
    */
  abstract class SectionManager {
    /** the direct parent sections of this section
      */
    def parentSectionNames: Array[String];

    /** the last section opened
      */
    def lastSectionGIndex: Long;

    /** returns the gIndexes of the sections that are still open
      */
    def openSectionsGIndexes(): Iterator[Long];

    /** sets info values of an open section.
      *
      * references should be references to gIndex of the root sections this section refers to.
      */
    def setSectionInfo(gIndex: Long, references: Map[String, Long]): Unit;

    /** returns the gIndex of a newly opened section
      */
    def openSection(): Long;

    /** closes the given section
      */
    def closeSection(gIndex: Long);

    /** Information on an open section
      */
    def openSectionInfo(gIndex: Long): String;
  }

  /** base class for an object that handles values for the given metaInfo
    *
    * metaInfo should be in the section handled by sectionManager.
    * This makes sense only for concrete meta infos.
    */
  abstract class MetaDataManager(
    val metaInfo: MetaInfoRecord
  ) {
    /** Scection manager of the section of this value
      */
    def sectionManager: SectionManager;

    /** Converts a json value to a long
      */
    final def convertJValue_i(value: JValue, shouldThrow: Boolean = true): Option[Long] = {
      value match {
        case JInt(i) =>
          Some(i.longValue)
        case JDecimal(d) =>
          Some(d.longValue)
        case JDouble(d) =>
          Some(d.longValue)
        case JNothing =>
          None
        case _ =>
          if (shouldThrow)
            throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting integer")
          None
      }
    }

    /** Converts a json value to a floating point double
      */
    final def convertJValue_f(value: JValue, shouldThrow: Boolean = true): Option[Double] = {
      value match {
        case JInt(i) =>
          Some(i.doubleValue)
        case JDecimal(d) =>
          Some(d.doubleValue)
        case JDouble(d) =>
          Some(d)
        case JNothing =>
          None
        case _ =>
          if (shouldThrow)
            throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting a floating point")
          None
      }
    }

    /** Converts a json value to a json dictionary
      */
    final def convertJValue_D(value: JValue, shouldThrow: Boolean = true): Option[JObject] = {
      value match {
        case JObject(obj) =>
          Some(JObject(obj))
        case JNothing =>
          None
        case _ =>
          if (shouldThrow)
            throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting a json dictionary")
          None
      }
    }

    /** Converts a json value to a string
      */
    final def convertJValue_C(value: JValue, shouldThrow: Boolean = true): Option[String] = {
      value match {
        case JString(s) =>
          Some(s)
        case JNothing =>
          None
        case _ =>
          if (shouldThrow)
            throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting a floating point")
          None
      }
    }

    /** Converts a json value to a Base64 encoded binary value
      */
    final def convertJValue_B64(value: JValue, shouldThrow: Boolean = true): Option[String] = {
      value match {
        case JString(s) =>
          Some(s)
        case JArray(arr) =>
          val byteArray = arr.flatMap {
            case JNothing =>
              None
            case JInt(i) =>
              if (i < Byte.MinValue || i > 255)
                throw new InvalidAssignementException(metaInfo, s"value $value out of bounds for Byte")
              Some(i.byteValue)
            case _ =>
              throw new InvalidAssignementException(metaInfo, s"unexpected value ($value) for Byte")
          }.toArray
          Some(Base64.b64EncodeStr(byteArray))
        case JNothing =>
          None
        case _ =>
          if (shouldThrow)
            throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting a base64 encoded value")
          None
      }
    }

    /** Adds value to the section the value is in
      */
    def addValue(value: JValue, gIndex: Long = -1): Unit; /*= {
      throw new InvalidAssignementException(metaInfo, "addValue not supported")
    }*/

    /** Adds a floating point value
      */
    def addRealValue(value: Double, gIndex: Long = -1): Unit; /*= {
      throw new InvalidAssignementException(metaInfo, "addRealValue not supported")
    }*/

    /** Adds a new array of the given size
      */
    def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit; /*= {
      throw new InvalidAssignementException(metaInfo, "addArrayValue not supported")
    }*/

    /** Adds values to the last array added
      */
    def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit; /*= {
      throw new InvalidAssignementException(metaInfo, "setArrayValues not supported")
    }*/

    /** Adds a new array with the given values
      */
    def addArrayValues(values: NArray, gIndex: Long = -1): Unit; /*= {
      addArrayValue(values.getShape().map( _.toLong).toSeq, gIndex)
      setArrayValues(values, gIndex = gIndex)
    }*/
  }

  /** dummy class that ignores input, useful for things that should be ignored
    */
  abstract class DummyMetaDataManager(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {

    /** Adds a floating point value
      */
    override def addRealValue(value: Double, gIndex: Long = -1): Unit = { }

    /** Adds a new array of the given size
      */
    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = { }

    /** Adds values to the last array added
      */
    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = { }

    /** Adds a new array with the given values
      */
    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = { }
  }

  /** abstact class to handle integer scalar values
    */
  abstract class MetaDataManager_i(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {

    def dispatch_i(value: Long, gIndex: Long);

    override def addValue(value: JValue, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      convertJValue_i(value) match {
        case Some(v) => dispatch_i(v, gI)
        case None => ()
      }
    }

    override def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      dispatch_i(value.longValue, gI)
    }
    
    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = {
      if (!(shape.isEmpty || shape.length == 1 && shape(0) == 1))
        throw new InvalidAssignementException(metaInfo, "tried to add an non scalar array value to a scalar integer")
    }

    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      offset match {
        case None =>
          ()
        case Some(o) =>
          if (!o.isEmpty && (o.length != 1 || o(0) != 0))
            throw new InvalidAssignementException(metaInfo, s"invalid offset ${o.mkString("[",",","]")} for scalar value")
      }
      dispatch_i(values.getLong(0), gI)
    }

    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      dispatch_i(values.getLong(0), gI)
    }
  }

  /** abstact class to handle floating point scalar values
    */
  abstract class MetaDataManager_f(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {

    def dispatch_f(value: Double, gIndex: Long);

    override def addValue(value: JValue, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      convertJValue_f(value) match {
        case Some(d) => dispatch_f(d, gI)
        case None => ()
      }
    }

    override def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      dispatch_f(value, gI)
    }
    
    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = {
      if (!(shape.isEmpty || shape.length == 1 && shape(0) == 1))
        throw new InvalidAssignementException(metaInfo, "tried to add an non scalar array value to a scalar integer")
    }

    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      offset match {
        case None =>
          ()
        case Some(o) =>
          if (!o.isEmpty && (o.length != 1 || o(0) != 0))
            throw new InvalidAssignementException(metaInfo, s"invalid offset ${o.mkString("[",",","]")} for scalar value")
      }
      dispatch_f(values.getDouble(0), gI)
    }

    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      dispatch_f(values.getDouble(0), gI)
    }
  }

  /** abstact class to handle json dictionary scalar values
    */
  abstract class MetaDataManager_D(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {
    def dispatch_D(value: JObject, gIndex: Long);

    override def addValue(value: JValue, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      convertJValue_D(value) match {
        case Some(obj) => dispatch_D(obj, gI)
        case None      => ()
      }
    }

    override def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      throw new InvalidAssignementException(metaInfo, "addRealValue not supported")
    }

    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = {
      if (shape.length != 1)
        throw new InvalidAssignementException(metaInfo, "tried to add an non scalar array value to a scalar dictionary")
    }

    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      offset match {
        case None =>
          ()
        case Some(o) =>
          if (!o.isEmpty && (o.length != 1 || o(0) != 0))
            throw new InvalidAssignementException(metaInfo, s"invalid offset ${o.mkString("[",",","]")} for scalar value")
      }
      /*JsonUtils.parseStr(values.getString(0)) match {
        case JObject(obj) =>
          dispatch_D(JObject(obj), gI)
        case JNothing =>
          ()
        case _ =>
          throw new InvalidAssignementException(metaInfo, s"invalid value ${values.getString(0)} when expecting a dictionary")
      }*/
    }

    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = {
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      /*JsonUtils.parseStr(values.getString(0)) match {
        case JObject(obj) =>
          dispatch_D(JObject(obj), gI)
        case JNothing =>
          ()
        case _ =>
          throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting a dictionary")
      }*/
    }
  }

  /** abstact class to handle string scalar values
    */
  abstract class MetaDataManager_C(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {

    def dispatch_C(value: String, gIndex: Long);

    override def addValue(value: JValue, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      convertJValue_C(value) match {
        case Some(s) =>
          dispatch_C(s, gI)
        case None =>
          ()
      }
    }

    override def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      throw new InvalidAssignementException(metaInfo, "addRealValue not supported")
    }

    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = {
      if (shape.length != 1)
        throw new InvalidAssignementException(metaInfo, "tried to add an non scalar array value to a scalar string")
    }

    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      offset match {
        case None =>
          ()
        case Some(o) =>
          if (!o.isEmpty && (o.length != 1 || o(0) != 0))
            throw new InvalidAssignementException(metaInfo, s"invalid offset ${o.mkString("[",",","]")} for scalar value")
      }
      //dispatch_C(values.getString(0), gI)
    }

    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      //dispatch_C(values.getString(0), gI)
    }
  }

  /** abstact class to handle binary blob scalar values
    */
  abstract class MetaDataManager_B64(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {
    def dispatch_B64(value: String, gIndex: Long);

    override def addValue(value: JValue, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      convertJValue_B64(value) match {
        case Some(s) =>
          dispatch_B64(s, gI)
        case None =>
          ()
      }
    }

    override def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      throw new InvalidAssignementException(metaInfo, "addRealValue not supported")
    }

    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = {
      if (shape.length != 1)
        throw new InvalidAssignementException(metaInfo, "tried to add an non scalar array value to a scalar dictionary")
    }

    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      offset match {
        case None =>
          ()
        case Some(o) =>
          if (!o.isEmpty && (o.length != 1 || o(0) != 0))
            throw new InvalidAssignementException(metaInfo, s"invalid offset ${o.mkString("[",",","]")} for scalar value")
      }
      //dispatch_B64(values.getString(0), gI)
    }

    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = {
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      //dispatch_B64(values.getString(0))
    }
  }

  /** returns shape an a sample elements of the nested json array
    *
    * if given expectedRank is the expected rank of the array, then the returned
    * shape will not be bigger than than expectedRank, it might still be smaller
    * and the caller is expected to handle that error
    */
  def shapeAndSampleEl(value: JValue, expectedRank: Int = -1): (Seq[Long],JValue) = {
    val shape: ListBuffer[Long] = ListBuffer[Long]()
    var el: JValue = value
    var missingRank: Int = expectedRank
    while (missingRank != 0) {
      el match {
        case JArray(arr) =>
          shape.append(arr.count{
            case JNothing => false
            case _ => true}.toLong)
          missingRank -= 1
          if (arr.isEmpty) {
            // if missingRank > 0 we could throw/warn, bt we leave it to the caller
            missingRank = 0
            el = JNothing
          } else {
            el = arr(0)
          }
        case _ =>
          // if missingRank > 0 we could throw/warn, bt we leave it to the caller
          missingRank = 0
      }
    }
    shape.toSeq -> el
  }

  /** If shape is not compatible with refShape throws an exception
    */
  def shapeCheck(metaInfo: MetaInfoRecord, shape: Seq[Long], refShape: Seq[Either[Long, String]]): Unit = {
    if (shape.length != refShape.length)
      throw new InvalidAssignementException(metaInfo, s"invalid shape.length (${shape.length} vs ${refShape.length})")
    for ((expected,actual) <- refShape.zip(shape)) {
      expected match {
        case Left(i) =>
          if (i != actual)
            throw new InvalidAssignementException(metaInfo, s"shape $shape is not compatible with the expected one ${refShape.mkString("[",",","]")}")
        case Right(s) =>
          ()
      }
    }
  }

  /** An iterator on a rectangular JArray of known shape for a given meta info
    */
  class RectangularJArrayIterator(
    val metaInfo: MetaInfoRecord,
    val shape: Seq[Long],
    var el: JValue,
    var iterators: List[List[JValue]] = Nil,
    var depth: Int = 0,
    idx: Option[Seq[Long]] = None,
    offset0: Option[Seq[Long]] = None
  ) extends Iterator[(Array[Long],JValue)] {
    val offset = Array.fill[Long](shape.size)(0)
    if (!offset0.isEmpty)
      offset0.get.copyToArray(offset)
    val index = Array.fill[Long](shape.size)(0)
    if (!idx.isEmpty)
      idx.get.copyToArray(index)
    else
      offset.copyToArray(index)
    val prevIndex = Array.fill[Long](shape.size)(-1)

    /** goes on on the same level or up until it can progress
      */
    def advance(): Unit = {

      iterators match {
        case currentList :: otherLists =>
          currentList.dropWhile(_ == JNothing) match {
            case newEl :: newList =>
              el = newEl
              iterators = newList :: otherLists
              index(depth - 1) += 1
            case Nil =>
              depth -= 1
              index(depth) = offset(depth)
              iterators = otherLists
              advance()
          }
        case Nil =>
          el = JNothing
      }
    }
 
    /** checks if the iterator ended properly
      */
    def checkEnd(): Unit = {
      val leftOver = el
      el = JNothing
      var invalid: Boolean = false
      for ((i, j) <- shape.zip(index)) {
        if (i != j + 1)
          invalid = true
      }
      val invalidMsg = if (invalid)
        s"iterator finished with index ${index.mkString("[",",","]")} when shape is ${shape.mkString("[",",","]")}"
      else
        ""
      if (leftOver != JNothing)
        throw new InvalidAssignementException(
          metaInfo, 
          s"array finished with leftover objects, $invalidMsg, left over: ${JsonUtils.prettyStr(leftOver)}")
      if (invalid)
        throw new InvalidAssignementException(metaInfo, invalidMsg)
    }

    /** Goes deeper until it reaches the wanted depth (rank)
      */
    def goToRank(): Unit = {
      while (depth != shape.size) {
        el match {
          case JArray(arr) =>
            arr.dropWhile( _ == JNothing) match {
              case newEl :: rest =>
                el = newEl
                iterators =  rest :: iterators
                depth += 1
              case Nil =>
                checkEnd()
                return
            }
          case JNothing =>
            checkEnd()
          case _ =>
            checkEnd()
        }
      }
    }

    // guarantee that the initial state is valid state
    // this simplifies hasNext, next
    goToRank()

    /** returns true if the iterator has more elements
      */
    def hasNext: Boolean = el != JNothing

    /** goes to the next element and returns it
      * 
      * The index array will be invalidated with the next call to next()
      */
    def next(): (Array[Long], JValue) = {
      index.copyToArray(prevIndex)
      val prevEl = el
      advance()
      goToRank()
      (prevIndex, prevEl)
    }
  }

  /** converts a JValue to an array
    *
    * assumes that createArray returns a canonically ordered array with the given shape
    */
  def convertJValueToArray[T](
    metaInfo: MetaInfoRecord,
    value: JValue,
    createArray: (Seq[Long]) => T,
    setValueAtFlatIndex: (T, Long, JValue) => Unit
  ): T = {
    val expectedShape = metaInfo.shape.getOrElse(Seq())
    val (shape, sampleVal) = GenericBackend.shapeAndSampleEl(value, expectedRank = expectedShape.length)
    GenericBackend.shapeCheck(metaInfo, shape, expectedShape)
    val iter = new RectangularJArrayIterator(
      metaInfo = metaInfo,
      shape = shape,
      el = value)
    val array = createArray(shape)
    var ii: Long = 0
    while (iter.hasNext) {
      val (index, el) = iter.next()
      setValueAtFlatIndex(array, ii, el)
      ii += 1
    }
    array
  }

  abstract class ArrayMetaDataManager(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {

    def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      throw new InvalidAssignementException(metaInfo, "addRealValue not supported")
    }
  }

  /** meta info for array of ints
    */
  abstract class ArrayMetaDataManager_i32(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): NArray = {
      NArray.factory(DataType.INT, shape.map(_.intValue).toArray)
    }

    def convertAllJValue_i32(value: JValue): Int = {
      convertJValue_i(value) match {
        case Some(longVal) =>
          if (longVal < Int.MinValue || longVal > Int.MaxValue)
            throw new InvalidAssignementException(metaInfo, s"Out of bounds value $value for type i32")
          longVal.intValue
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_i32(array: NArray, i: Long, value: Int): Unit = {
      array.setInt(i.intValue, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[NArray](metaInfo, value, createArray _, {
        (array: NArray, i: Long, value: JValue) =>
        setFlatIndex_i32(array, i, convertAllJValue_i32(value))
      }), gIndex)
    }
  }

  /** meta info for array of longs
    */
  abstract class ArrayMetaDataManager_i64(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): NArray = {
      NArray.factory(Long.getClass(), shape.map(_.intValue).toArray)
    }

    def convertAllJValue_i64(value: JValue): Long = {
      convertJValue_i(value) match {
        case Some(i) =>
          i
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_i64(array: NArray, i: Long, value: Long): Unit = {
      array.setLong(i.intValue, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[NArray](metaInfo, value, createArray _, {
        (array: NArray, i: Long, value: JValue) =>
        setFlatIndex_i64(array, i, convertAllJValue_i64(value))
      }), gIndex)
    }
  }

  /** meta info for array of floats
    */
  abstract class ArrayMetaDataManager_f32(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): NArray = {
      NArray.factory(Float.getClass(), shape.map(_.intValue).toArray)
    }

    def convertAllJValue_f32(value: JValue): Float = {
      convertJValue_f(value) match {
        case Some(d) =>
          d.floatValue
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_f32(array: NArray, i: Long, value: Float): Unit = {
      array.setFloat(i.intValue, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[NArray](metaInfo, value, createArray _, {
        (array: NArray, i: Long, value: JValue) =>
        setFlatIndex_f32(array, i, convertAllJValue_f32(value))
      }), gIndex)
    }
  }

  /** meta info for array of doubles
    */
  abstract class ArrayMetaDataManager_f64(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): NArray = {
      NArray.factory(Float.getClass(), shape.map(_.intValue).toArray)
    }

    def convertAllJValue_f64(value: JValue): Double = {
      convertJValue_f(value) match {
        case Some(d) =>
          d
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_f64(array: NArray, i: Long, value: Double): Unit = {
      array.setDouble(i.intValue, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[NArray](metaInfo, value, createArray _, {
        (array: NArray, i: Long, value: JValue) =>
        setFlatIndex_f64(array, i, convertAllJValue_f64(value))
      }), gIndex)
    }
  }

  /** meta info for array of bytes
    */
  abstract class ArrayMetaDataManager_b(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): NArray = {
      NArray.factory(Byte.getClass(), shape.map(_.intValue).toArray)
    }

    def convertAllJValue_b(value: JValue): Byte = {
      convertJValue_i(value) match {
        case Some(i) =>
          if (i < Byte.MinValue || i > 255)
            throw new InvalidAssignementException(metaInfo, s"value $value out of bounds for Byte")
          i.byteValue
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_b(array: NArray, i: Long, value: Byte): Unit = {
      array.setByte(i.intValue, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[NArray](metaInfo, value, createArray _, {
        (array: NArray, i: Long, value: JValue) =>
        setFlatIndex_b(array, i, convertAllJValue_b(value))
      }), gIndex)
    }
  }

  /** meta info for array of strings
    */
  abstract class ArrayMetaDataManager_C(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): ArrayString = {
      new ArrayString(shape.map(_.intValue).toArray)
    }

    def convertAllJValue_C(value: JValue): String = {
      convertJValue_C(value) match {
        case Some(s) =>
          s
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_C(array: ArrayString, i: Long, value: String): Unit = {
      val index = array.getIndex() // inefficient
      index.setCurrentCounter(i.intValue)
      array.set(index, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[ArrayString](metaInfo, value, createArray _, {
        (array: ArrayString, i: Long, value: JValue) =>
        setFlatIndex_C(array, i, convertAllJValue_C(value))
      }), gIndex)
    }
  }

  /** meta info for array of byte arrays (blobs)
    *
    * This should be improved, currently data is stored as Base64 url encoded
    * strings, but ArraySequence of bytes is probably better and should be
    * evaluated
    */
  abstract class ArrayMetaDataManager_B64(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): ArrayString = {
      new ArrayString(shape.map(_.intValue).toArray)
    }

    def convertAllJValue_B64(value: JValue): String = {
      convertJValue_C(value) match {
        case Some(s) =>
          s
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_B64(array: ArrayString, i: Long, value: String): Unit = {
      val index = array.getIndex()  // inefficient
      index.setCurrentCounter(i.intValue)
      array.set(index, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[ArrayString](metaInfo, value, createArray _, {
        (array: ArrayString, i: Long, value: JValue) =>
        setFlatIndex_B64(array, i, convertAllJValue_B64(value))
      }), gIndex)
    }
  }

  /** meta info for array of byte arrays (blobs)
    *
    * This should be improved, currently data is stored as Base64 url encoded
    * strings, but ArraySequence of bytes representing the UTF8 encoded json
    * is probably better and should be evaluated
    */
  abstract class ArrayMetaDataManager_D(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): ArrayString = {
      new ArrayString(shape.map(_.intValue).toArray)
    }

    def convertAllJValue_D(value: JValue): String = {
      convertJValue_D(value) match {
        case Some(d) =>
          JsonUtils.normalizedStr(value)
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_D(array: ArrayString, i: Long, value: String): Unit = {
      val index = array.getIndex() // inefficient
      index.setCurrentCounter(i.intValue)
      array.set(index, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[ArrayString](metaInfo, value, createArray _, {
        (array: ArrayString, i: Long, value: JValue) =>
        setFlatIndex_D(array, i, convertAllJValue_D(value))
      }), gIndex)
    }
  }

}

abstract class GenericBackend(
  val metaInfoEnv: MetaInfoEnv
) extends ParserBackend {

  /** the manger for the sections
    */
  def sectionManagers: Map[String, GenericBackend.SectionManager];

  /** mangers for data
    */
  def metaDataManagers: Map[String, GenericBackend.MetaDataManager];

  /** returns the sections that are still open
    *
    * sections are identified by metaName and their gIndex
    */
  override def openSections(): Iterator[(String, Long)] = {
    sectionManagers.foldLeft(Iterator.empty: Iterator[(String,Long)]){ (it: Iterator[(String,Long)], el: (String, GenericBackend.SectionManager)) =>
      val (sectionName, sectionManager) = el
      it ++ Iterator.continually(sectionName).zip(sectionManager.openSectionsGIndexes())
    }
  }

  /** returns information on an open section (for debugging purposes)
    */
  override def openSectionInfo(metaName: String, gIndex: Long): String = {
    sectionManagers(metaName).openSectionInfo(gIndex)
  }

  /** opens a new section.
    */
  override def openSection(metaName: String): Long = {
    sectionManagers(metaName).openSection()
  }

  /** sets info values of an open section.
    *
    * references should be references to gIndex of the root sections this section refers to.
    */
  override def setSectionInfo(metaName: String, gIndex: Long, references: Map[String, Long]) = {
    sectionManagers(metaName).setSectionInfo(gIndex, references)
  }

  /** closes a section
    *
    * after this no other value can be added to the section.
    * metaName is the name of the meta info, gIndex the index of the section
    */
  override def closeSection(metaName: String, gIndex: Long): Unit = {
    sectionManagers(metaName).closeSection(gIndex)
  }

  /** Adds a json value corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    */
  override def addValue(metaName: String, value: JValue, gIndex: Long = -1): Unit = {
    metaDataManagers(metaName).addValue(value, gIndex)
  }

  /** Adds a floating point value corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    */
  override def addRealValue(metaName: String, value: Double, gIndex: Long = -1): Unit = {
    metaDataManagers(metaName).addRealValue(value, gIndex)
  }

  /** Adds a new array value of the given size corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    * The array is unitialized.
    */
  override def addArrayValue(metaName: String, shape: Seq[Long], gIndex: Long = -1): Unit = {
    metaDataManagers(metaName).addArrayValue(shape, gIndex)
  }

  /** Adds values to the last array added
    */
  override def setArrayValues(
    metaName: String, values: NArray,
    offset: Option[Seq[Long]] = None,
    gIndex: Long = -1): Unit = {
    metaDataManagers(metaName). setArrayValues(values, offset, gIndex)
  }
  
  /** Adds an array value with the given array values
    */
  override def addArrayValues(metaName: String, values: NArray, gIndex: Long = -1): Unit = {
    metaDataManagers(metaName).addArrayValues(values, gIndex)
  }

}

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

    def addValue(metaInfo: MetaInfoRecord, value: JValue) {
      cachedSimpleValues.get(metaInfo.name) match {
        case Some(vals) =>
          vals.append(value)
        case None =>
          cachedSimpleValues += (metaInfo.name -> ListBuffer(value))
      }
    }

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

    def addArrayValues(metaInfo: MetaInfoRecord, values: NArray) {
        cachedArrayValues.get(metaInfo.name) match {
        case Some(vals) =>
          vals.append(values)
        case None =>
          cachedArrayValues += (metaInfo.name -> ListBuffer(values))
      }
    }

    def addSubsection(metaInfo: MetaInfoRecord, value: CachingSection) {
      cachedSubSections.get(metaInfo.name) match {
        case Some(vals) =>
          vals.append(value)
        case None =>
          cachedSubSections += (metaInfo.name -> ListBuffer(value))
      }
    }

    def onClose(backend: CachingBackend, sectionManager: CachingSectionManager): Unit = { }
  }

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

    def addValue(valueMetaInfo: MetaInfoRecord, value: JValue, gIndex: Long): Unit = {
      val gI = if (gIndex == -1)
        lastSectionGIndex
      else
        gIndex
      openSections(gI).addValue(valueMetaInfo, value)
    }

    def setArrayValues(valueMetaInfo: MetaInfoRecord, value: NArray, offset: Option[Seq[Long]], gIndex: Long): Unit = {
      val gI = if (gIndex == -1)
        lastSectionGIndex
      else
        gIndex
      openSections(gI).setArrayValues(valueMetaInfo, value, offset)
    }


    def addArrayValues(valueMetaInfo: MetaInfoRecord, value: NArray, gIndex: Long): Unit = {
      val gI = if (gIndex == -1)
        lastSectionGIndex
      else
        gIndex
      openSections(gI).addArrayValues(valueMetaInfo, value)
    }
  }

  class CachingMetaDataManager_i(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.MetaDataManager_i(metaInfo) {

    def dispatch_i(value: Long, gIndex: Long): Unit = {
      sectionManager.addValue(metaInfo, JInt(value), gIndex)
    }
  }

  class CachingMetaDataManager_f(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.MetaDataManager_f(metaInfo) {

    def dispatch_f(value: Double, gIndex: Long): Unit = {
      sectionManager.addValue(metaInfo, JDouble(value), gIndex)
    }
  }

  class CachingMetaDataManager_B64(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.MetaDataManager_B64(metaInfo) {

    def dispatch_B64(value: String, gIndex: Long): Unit = {
      sectionManager.addValue(metaInfo, JString(value), gIndex)
    }
  }

  class CachingMetaDataManager_C(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.MetaDataManager_C(metaInfo) {

    def dispatch_C(value: String, gIndex: Long): Unit = {
      sectionManager.addValue(metaInfo, JString(value), gIndex)
    }
  }

  class CachingMetaDataManager_D(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.MetaDataManager_D(metaInfo) {

    def dispatch_D(value: JObject, gIndex: Long): Unit = {
      sectionManager.addValue(metaInfo, value, gIndex)
    }
  }

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

  class CachingMetaDataManager_Ai32(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_i32(metaInfo) with CachingArray {
  }

  class CachingMetaDataManager_Ai64(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_i64(metaInfo) with CachingArray {
  }

  class CachingMetaDataManager_Af32(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_f32(metaInfo) with CachingArray {
  }

  class CachingMetaDataManager_Af64(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_f64(metaInfo) with CachingArray {
  }

  class CachingMetaDataManager_Ab(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_b(metaInfo) with CachingArray {
  }

  class CachingMetaDataManager_AB64(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_B64(metaInfo) with CachingArray {
  }

  class CachingMetaDataManager_AC(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_C(metaInfo) with CachingArray {
  }

  class CachingMetaDataManager_AD(
    metaInfo: MetaInfoRecord,
    val sectionManager: CachingSectionManager
  ) extends GenericBackend.ArrayMetaDataManager_D(metaInfo) with CachingArray {
  }

  class InvalidMetaInfoException(
    metaInfo: MetaInfoRecord, msg: String
  ) extends Exception(s"${metaInfo.name} is invalid: $msg, metaInfo: ${JsonUtils.prettyStr(metaInfo.toJValue())}") {}

  class MetaCompilationException(
    metaInfo: MetaInfoRecord, msg: String
  ) extends Exception(s"Error while compiling meta info ${metaInfo.name}: $msg, metaInfo: ${JsonUtils.prettyStr(metaInfo.toJValue())}") {}

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

class CachingBackend(
  metaInfoEnv: MetaInfoEnv,
  val sectionManagers: Map[String, CachingBackend.CachingSectionManager],
  val metaDataManagers: Map[String, GenericBackend.MetaDataManager]
) extends GenericBackend(metaInfoEnv) {

}
