package eu.nomad_lab.parsing_queue

import java.io._
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

import com.typesafe.scalalogging.StrictLogging
import eu.nomad_lab.QueueMessage.{CalculationParserResult, CalculationParserRequest, ToBeNormalizedQueueMessage}
import eu.nomad_lab.parsers.ParseResult
import eu.nomad_lab.parsers.OptimizedParser
import eu.nomad_lab.parsers.ParseResult._
import eu.nomad_lab.{JsonSupport, CompactSha, TreeType, parsers}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipFile}
import org.apache.commons.compress.utils.IOUtils
import org.json4s._
import eu.{nomad_lab => lab}
import scala.collection.mutable
import scala.util.control.NonFatal


object CalculationParser extends StrictLogging {

  /** An exception that indicates failure during parsing.
    *
    * @param message Incoming message to parse
    * @param calculationParser
    * @param msg
    * @param what
    */
  class CalculationParserException(
                                    message: CalculationParserRequest,
                                    calculationParser: CalculationParser,
                                    msg: String,
                                    what: Throwable = null
                                  ) extends Exception(
    s"$msg when parsing ${JsonSupport.writeStr(message)} (${calculationParser.ucRoot})",
    what
  )
}
/** This class implements methods to run the uncompress the archive (if required) and to run parser.
  *
  */

class CalculationParser (
                        val ucRoot: String,
                        val parsedJsonPath: String,
                        val parserCollection: parsers.ParserCollection,
                        val replacements: Map[String, String]
                        ) extends  StrictLogging {
  val alreadyUncompressed = mutable.Set[String]()
  var lastArchivePath: String = ""
  var lastUncompressRoot: Option[Path] = None
  val cachedParsers = mutable.Map[String, OptimizedParser]()

  /** Hierarchically extract all the files from the current level on.
    *
    */
  def uncompress(inMsg: CalculationParserRequest, uncompressRoot:Path):Option[Path] = {
    if ((!lastArchivePath.isEmpty || lastUncompressRoot.isDefined || !alreadyUncompressed.isEmpty) && (inMsg.treeFilePath != lastArchivePath || lastUncompressRoot != Some(uncompressRoot))) {
      lastUncompressRoot match {
        case Some(toRm) =>
          lab.LocalEnv.deleteRecursively(toRm)
        case None => ()
      }
      alreadyUncompressed.clear()
    }
    lastUncompressRoot = Some(uncompressRoot)
    lastArchivePath = inMsg.treeFilePath
    inMsg.treeType match {
      case TreeType.Zip =>
        val dirPath = Paths.get(inMsg.relativeFilePath).getParent
        val prefix = {
          val p = dirPath.toString
          if (p.isEmpty() || p == ".")
            ""
          else
            p + "/"
        }
        if (!alreadyUncompressed.contains(prefix)) {
          alreadyUncompressed += prefix
          val zipFile = new ZipFile(inMsg.treeFilePath)
          try {
            val entries = zipFile.getEntries()
            while (entries.hasMoreElements()) {
              val zipEntry: ZipArchiveEntry = entries.nextElement()
              if (!zipEntry.isDirectory && !zipEntry.isUnixSymlink) {
                //Only check non directory and symlink for now; TODO: Add support to read symlink
                if (zipEntry.getName.startsWith(prefix)) {
                  val destination = uncompressRoot.resolve(zipEntry.getName).toFile
                  if(destination.exists()) {
                    logger.debug("uncompress: File  already exists! Skipping the uncompression step!!")
                  } else {
                    destination.getParentFile.mkdirs()
                    val zIn: InputStream = zipFile.getInputStream(zipEntry)
                    val out: OutputStream = new FileOutputStream(destination)
                    IOUtils.copy(zIn, out)
                    IOUtils.closeQuietly(zIn)
                    out.close()
                  }
                }
              }
            }
          } finally {
            zipFile.close()
          }
        }
        Some(uncompressRoot.resolve(inMsg.relativeFilePath).toAbsolutePath)
      case _ => None
    }
  }

  /** Main function that handles the incoming request to parse a file. Check if the parser, file to parse exists and whether to overwrite if parsedFileOutputPath exists.
    * Finally uncompress and call the parser and check the parser result.
    *
    */
  def handleParseRequest(inMsg: CalculationParserRequest): CalculationParserResult = {
    var didExist = false
    var created = true
    var errorMessage:Option[String] = None
    var parserInfo: JValue = JNothing
    var parsedFileUri:Option[String] = None
    var parsedFilePath:Option[String] = None
    var pResult:ParseResult = ParseFailure
    parserCollection.parsers.get(inMsg.parserName) match {
      case None =>
        errorMessage = Some(s"Could not find parser named ${inMsg.parserName}")
      case Some(parser) =>
        parserInfo = parser.parserInfo
        val parserId = (parser.parserInfo \ "parserId") match {
          case JString(v) => v
          case _ => inMsg.parserName + "_undefined"
        }
        var repl = replacements
        repl += ("parserName" -> inMsg.parserName)
        repl += ("parserId" -> parserId)
        val archiveIdRe = "^nmd://(R[-_a-zA-Z0-9]{28})(?:/.*)?$".r
        val nomadUrlRe = "^nmd://.*$".r
        val archiveId = inMsg.mainFileUri match {
          case archiveIdRe(aId) => aId
          case nomadUrlRe() =>
            logger.warn(s"encountred undexpected non archive nomadUrl ${inMsg.mainFileUri}")
            "undefined"
          case _ =>
            "undefined"
        }
        repl += ("archiveId" -> archiveId)
        repl += ("archiveIdPrefix" -> archiveId.take(3))
        val uncompressTarget = Paths.get(lab.LocalEnv.makeReplacements(repl, ucRoot))
        uncompress(inMsg,uncompressTarget) match {
          case Some(filePath) =>
            val optimizedParser = cachedParsers.get(inMsg.parserName) match {
              case Some(optParser) =>
                cachedParsers.remove(inMsg.parserName)
                optParser
              case None =>
                parser.optimizedParser(Seq())
            }
            val uncompressedRoot = uncompressTarget.toFile()
            val cSha = CompactSha()
            cSha.updateStr(inMsg.mainFileUri)
            val parsedFileId = cSha.gidStr("P")
            repl += ("parsedFileId" -> parsedFileId)
            repl += ("parsedFileIdPrefix" -> parsedFileId.take(3))
            val calculationId = "C" + parsedFileId.drop(1)
            repl += ("calculationId" -> calculationId)
            repl += ("calculationIdPrefix" -> calculationId.take(3))
            val pFURI = "nmd://" +  parsedFileId
            val parsedFileOutputPath = Paths.get(lab.LocalEnv.makeReplacements(repl, parsedJsonPath))
            if (parsedFileOutputPath.toFile.exists()) // TODO: inMsg now contains overwrite flag; add support for the flag
              didExist = true
            else
              parsedFileOutputPath.getParent.toFile.mkdirs()
            val tmpFile = Files.createTempFile(parsedFileOutputPath.getParent, parsedFileId.take(5), ".tmp")
            val outWriter = new FileWriter(tmpFile.toFile)
            val extBackend: parsers.ParserBackendExternal = new parsers.JsonWriterBackend(optimizedParser.parseableMetaInfo, outWriter)
            try {
              pResult = optimizedParser.parseExternal(inMsg.mainFileUri,filePath.toString,extBackend,parser.name)
              outWriter.flush()
              outWriter.close()
              Files.move(tmpFile, parsedFileOutputPath, StandardCopyOption.ATOMIC_MOVE)
            } catch {
              case NonFatal(e) => errorMessage = Some(s"${parser.name} threw: $e when parsing $filePath")
            } finally {
              outWriter.close()
              if (optimizedParser.canBeReused)
                cachedParsers += (inMsg.parserName -> optimizedParser)
              else
                optimizedParser.cleanup()
            }
            println(s"ParserResult: $pResult when parsing $filePath by ${parser.name}" )
            pResult match {
              case ParseSuccess | ParseWithWarnings =>
                parsedFileUri = Some(pFURI)
                parsedFilePath = Some(parsedFileOutputPath.toString)
              case _ => ()
            }
          case None =>
            errorMessage = Some("Error uncompressing files")
        }
    }

    CalculationParserResult(
      parseResult = pResult,
      parserInfo = parserInfo,
      parsedFileUri = parsedFileUri,
      parsedFilePath = parsedFilePath,
      didExist = didExist,
      created = created,
      errorMessage = errorMessage,
      parseRequest = inMsg
    )
  }

  /** Cleans up the after successful completion of calculation parser
    */
  def cleanup(): Unit = {
    for ((_, optParser) <- cachedParsers)
      optParser.cleanup()
    cachedParsers.clear()
    lastUncompressRoot match {
      case Some(toRm) =>
        lab.LocalEnv.deleteRecursively(toRm)
      case None => ()
    }
  }
}
