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
import org.apache.tika.Tika
import com.typesafe.scalalogging.StrictLogging
import java.nio.charset.StandardCharsets
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharsetDecoder
import java.nio.charset.CoderResult
import java.nio.ByteBuffer
import java.nio.CharBuffer
import scala.util.matching.Regex

/** prefiltering on the ancillary files performed
  *
  * This is to make it possible to avoid extracting all files from an archive
  */
object AncillaryFilesPrefilter extends Enumeration {
  type AncillaryFilesPrefilter = Value
  val WholeUpload, WholeSubtree, SubtreeDepth1, SameLevelOnly, MainFileOnly = Value
}

/** describes the level of matching of the parser
  *
  *  lower matchPrioritz wins over larger, strong match over a weak one
  */
case class ParserMatch(
  val matchPriority: Int = 0,
  val weakMatch: Boolean = false
) {}

object ParserMatch {
  implicit def orderingByPriority[A <: ParserMatch]: Ordering[A] =
    Ordering.by(m => (m.matchPriority, m.weakMatch))
}

object MetaInfoOps {
  object Ops extends Enumeration {
    type Ops = Value;
    val AddWithRoots, RemoveWithDescendents = Value
  }
}

case class MetaInfoOps(
  op: MetaInfoOps.Ops.Value,
  regExps: Seq[String]
){}

/** represents a possible matching parser
  */
case class CandidateParser(
  val parserMatch: ParserMatch,
  val parserName: String,
  val parser: ParserGenerator
) {}

object CandidateParser {
  implicit def orderingByMatch[A <: CandidateParser]: Ordering[A] =
    Ordering.by(c => (c.parserMatch, c.parserName))
}

/** Describes an object able identify files it can parse and create a parser them
  *
  * all functions are expected to be reentrant and threadsafe
  */
trait ParserGenerator {
  /** unique name for this parser
    */
  def name: String

  /** description of the data that can be extracted by this parser
    */
  def parseableMetaInfo: MetaInfoEnv

  /** parser information: name, description,...
    */
  def parserInfo: JObject

  /** mime types to be checked as main files */
  def mainFileTypes: Seq[String]

  /** function that should decide if this main file can be parsed by this parser
    * looking at the first few kilobytes of it
    */
  def isMainFile(filePath: String, bytePrefix: Array[Byte], stringPrefix: Option[String]): Option[ParserMatch]

  /** returns an optimized parser that performs the actual parsing
    * 
    * The parser can excludes all the meta infos with names listed in the exclude
    * argument and those derived by them, then it adds all these explicitly
    * included meta infos and all their parents.
    *
    * All optimization are optional there is no guarantee that the resulting
    * parser really skips some data.
    */
  def optimizedParser(optimizations: Seq[MetaInfoOps]): OptimizedParser

  /** cleans up the resources allocaed by this parser generator
    *
    * All its optimized parsers should have already been cleaned and never used again
    * because they might rely on resources allocated by this parser generator,
    * for example files or directories.
    */
  def cleanup(): Unit;
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

  /** parses the file at the given path, calling the internal backend with the parser events
    *
    * parserName is used to identify the parser, mainly for logging/debugging
    */ 
  def parseInternal(mainFileUri: String, mainFilePath: String, backend: ParserBackendInternal, parserName: String): ParseResult.ParseResult

  /** parses the file at the given path, calling the external backend with the parser events
    *
    * parserName is used to identify the parser, mainly for logging/debugging
    */ 
  def parseExternal(mainFileUri: String, mainFilePath: String, backend: ParserBackendExternal, parserName: String): ParseResult.ParseResult

  /** clean up the external resources allocated by the parser
    */
  def cleanup(): Unit

  /** if the parsing had errors
    */
  def hadErrors: Boolean
}

object ParserBackendBase {
  /** Exception thrown when an incorrect backend calling sequence is detected
    * 
    * For example: open section before opening parsing sessions, emitting value before opening a section,...
    */
  class InvalidCallSequenceException(
    msg: String, what: Throwable = null
  ) extends Exception(msg, what) {}
}

/**Basic callbacks that are called by a streaming parser
  *
  * methods that should store or evaluate the data extracted by the parser
  */
trait ParserBackendBase {
  /** The metaInfoEnv this parser was optimized for
    */
  def metaInfoEnv: MetaInfoEnv;

  /** Started a parsing session
    */
  def startedParsingSession(
    mainFileUri: Option[String],
    parserInfo: JValue,
    parserStatus: Option[ParseResult.Value] = None,
    parserErrors: JValue = JNothing): Unit;

  /** finished a parsing session
    */
  def finishedParsingSession(
    parserStatus: Option[ParseResult.Value],
    parserErrors: JValue = JNothing,
    mainFileUri: Option[String] = None,
    parserInfo: JValue = JNothing): Unit;

  /** returns the sections that are still open
    *
    * sections are identified by metaName and their gIndex
    */
  def openSections(): Iterator[(String, Long)];

  /** returns information on an open section (for debugging purposes)
    */
  def sectionInfo(metaName: String, gIndex: Long): String;

  /** sets info values of an open section.
    *
    * references should be references to gIndex of the root sections this section refers to.
    */
  def setSectionInfo(metaName: String, gIndex: Long, references: Map[String, Long]): Unit;

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

  /** Adds a new array of the given size corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    * The array is unitialized.
    */
  def addArray(metaName: String, shape: Seq[Long], gIndex: Long = -1): Unit;

  /** Adds values to the last array added
    */
  def setArrayValues(
    metaName: String, values: NArray,
    offset: Option[Seq[Long]] = None,
    gIndex: Long = -1): Unit;
  
  /** Adds an array value with the given array values
    */
  def addArrayValues(metaName: String, values: NArray, gIndex: Long = -1): Unit = {
    addArray(metaName, values.getShape().map(_.toLong).toSeq);
    setArrayValues(metaName, values);
  }
}


/** Callbacks that are called by an internal streaming parser
  *
  * This kind of backend is in control of the GIndexes of the sections
  * and chooses them
  * The ReindexBackend can adapt a backend wanting to control gIndex
  * with one that wants to set indexes
  */
trait ParserBackendInternal extends ParserBackendBase {
  /** opens a new section, returning a valid gIndex
    */
  def openSection(metaName: String): Long;
}

/** Callbacks that are called by an external streaming parser
  *
  * Here index generation is controlled externally, this is good for external
  * parsers as they become effectively decoupled, and can generate the whole
  * stream of events without waiting for any answer (no latency or roundtrip)
  * A GenIndexBackend (to do), can adapt and external backend to an internal one
  */
trait ParserBackendExternal extends ParserBackendBase {
  /** Informs tha backend that a section with the given gIndex should be opened
    *
    * The index is assumed to be unused, it is an error to reopen an existing section.
    */
  def openSectionWithGIndex(metaName: String, gIndex: Long): Unit;
}

object ParserCollection {
  val tika = new Tika()

  /** Multiple parsers can handle the same file
    */
  class MultipleMatchException(
    parsers: Seq[ParserGenerator],
    filePath: String,
    bytePrefix: Array[Byte],
    stringPrefix: Option[String]
  ) extends Exception(s"Multiple parsers match file $filePath: ${parsers.map{ (p: ParserGenerator) => JsonUtils.prettyStr(p.parserInfo)}.mkString(", ")}") { }
}

/** A set of parsers that can parse a tree
  */
class ParserCollection(
  val parsers: Map[String, ParserGenerator]
) extends StrictLogging {

  /** internal mapping of parsers names organized by the mime type of their main file
    */
  val parsersByMimeType = {
    val byMimeType = mutable.Map[String,ListBuffer[String]]()
    parsers.foreach { case (parserName, parser) =>
       parser.mainFileTypes.foreach { mimeTypeRe =>
         byMimeType.get(mimeTypeRe) match {
           case Some(pList) =>
             pList.append(parserName)
           case None =>
             byMimeType += (mimeTypeRe -> ListBuffer(parserName))
         }
       }
    }
    byMimeType.map{ case (mimeType, parsers) =>
      mimeType.r -> parsers.toSeq
    }(breakOut): Array[(Regex, Seq[String])]
  }

  /** Scans the given file with the given parsers and returns candidate parsers
    *
    * Low level, normally you should use scanFile.
    */
  def scanWithParsers(
    parserNames: Seq[String],
    filePath: String,
    bytePrefix: Array[Byte],
    stringPrefix: Option[String],
    allowMultipleMatches: Boolean = false
  ): Seq[CandidateParser] = {
    parserNames.flatMap { (parserName: String) =>
      val parser = parsers(parserName)
      parser.isMainFile(filePath, bytePrefix, stringPrefix) match {
        case Some(parserMatch) =>
          Some(CandidateParser(parserMatch, parserName, parser))
        case None =>
          None
      }
    }
  }

  /** Scans the given file and returns the candidate parsers (unsorted)
    */
  def scanFile(filePath: String, bytePrefix: Array[Byte]): Seq[CandidateParser] = {
    val file = new java.io.File(filePath)
    val mimeType: String = ParserCollection.tika.detect(bytePrefix, file.getName())
    logger.debug(s"$filePath detected as $mimeType")
    val parsersDone = mutable.Set[String]()
    lazy val stringPrefix = {
      val utf8Decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
      utf8Decoder.onMalformedInput(CodingErrorAction.REPORT)
      utf8Decoder.onMalformedInput(CodingErrorAction.REPORT)
      val byteBuffer = ByteBuffer.wrap(bytePrefix)
      val strBuf = CharBuffer.allocate(bytePrefix.length)
      val utf8Decoding: CoderResult = utf8Decoder.decode(
        byteBuffer, strBuf, false)
      if (utf8Decoding.isError()) {
        val isoDecoder = StandardCharsets.ISO_8859_1.newDecoder()
        strBuf.clear()
        val isoDecoding: CoderResult = isoDecoder.decode(
          byteBuffer, strBuf, false)
        assert(!isoDecoding.isError())
      }
      strBuf.toString()
    }
    parsersByMimeType.foldLeft(Seq[CandidateParser]()){ case (seq, (mimeRe, parserNames)) =>
      mimeType match {
        case mimeRe() =>
          val parsersToDo = parserNames.filter(!parsersDone(_))
          if (!parsersToDo.isEmpty) {
            parsersDone ++= parsersToDo
            seq ++ scanWithParsers(parsersToDo, filePath, bytePrefix, Some(stringPrefix))
          } else {
            seq
          }
        case _ =>
          seq
      }
    }
  }
}
