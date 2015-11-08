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
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

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
