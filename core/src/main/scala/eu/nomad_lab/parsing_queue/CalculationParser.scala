package eu.nomad_lab.parsing_queue

import java.io._
import java.nio.file.{Path, Paths}

import com.typesafe.scalalogging.StrictLogging
import eu.nomad_lab.QueueMessage.{CalculationParserResult, CalculationParserRequest, ToBeNormalizedQueueMessage}
import eu.nomad_lab.parsers.ParseResult
import eu.nomad_lab.parsers.ParseResult._
import eu.nomad_lab.{JsonSupport, CompactSha, TreeType, parsers}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipFile}
import org.apache.commons.compress.utils.IOUtils
import org.json4s._
import eu.nomad_lab.parsers.SimpleExternalParserGenerator.makeReplacements
import scala.collection.mutable
import scala.util.control.NonFatal

object CalculationParser extends StrictLogging {
  class CalculationParserException(
                                    message: CalculationParserRequest,
                                    calculationParser: CalculationParser,
                                    msg: String,
                                    what: Throwable = null
                                  ) extends Exception(
    s"$msg when parsing ${JsonSupport.writeStr(message)} (${calculationParser.ucRoot})",
    what
  )
  /** Extract all the files at the current level and in the subfolder
    *
    * @param inMsg
  //    */
  def uncompress(inMsg: CalculationParserRequest, ucRoot:Path):Option[Path] = {
    inMsg.treeType match {
      case TreeType.Zip =>
        val prefix = Paths.get(inMsg.relativeFilePath).getParent.toString
        val zipFile = new ZipFile(inMsg.treeFilePath)
        val entries = zipFile.getEntries()
        while (entries.hasMoreElements()) {
          val zipEntry: ZipArchiveEntry = entries.nextElement()
          if (!zipEntry.isDirectory && !zipEntry.isUnixSymlink) {
            //Only check non directory and symlink for now; TODO: Add support to read symlink
            if (zipEntry.getName.contains(prefix)) {
              val destination = ucRoot.resolve(zipEntry.getName).toFile
              if(destination.exists()) {
                logger.info("uncompress: File  already exists! Skipping the uncompression step!!")
              }
              else{
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
        Some(ucRoot.resolve(inMsg.relativeFilePath).toAbsolutePath)
      case _ => None
    }
  }
}

class CalculationParser (
                        val ucRoot: String,
                        val parsedRoot: String,
                        val parserCollection: parsers.ParserCollection,
                        val replacements: Map[String, String]
                        ) extends  StrictLogging {
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
        val parserId = (parser.parserInfo \ "versionInfo" \ "version") match {
          case JString(v) => inMsg.parserName + "_" + v
          case _ => inMsg.parserName + "_undefined"
        }
        var repl = replacements
        repl += ("parserName" -> inMsg.parserName)
        repl += ("parserId" -> parserId)
        val archiveIdRe = "^nmd://(R[-_a-zA-z]{28})(?:/.*)?$".r
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
        val uncompressTarget = Paths.get(makeReplacements(repl, ucRoot))
        CalculationParser.uncompress(inMsg,uncompressTarget) match {
          case Some(filePath) =>
            val optimizer = parser.optimizedParser(Seq())
            val uncompressedRoot = uncompressTarget.toFile()
            val cSha = CompactSha()
            cSha.updateStr(inMsg.mainFileUri)
            val mfileUriSha = cSha.gidStr("P")
            repl += ("parsedFileId" -> mfileUriSha)
            repl += ("parsedFileIdPrefix" -> mfileUriSha.take(3))
            repl += ("calculationId" -> ("C" + mfileUriSha.drop(1)))
            repl += ("calculationIdPrefix" -> ("C" + mfileUriSha.drop(1)).take(3))
            val pFURI = "nmd://" +  mfileUriSha
            val parsedFileOutputPath = Paths.get(parsedRoot,mfileUriSha.substring(0,3), mfileUriSha + ".json")
            if (parsedFileOutputPath.toFile.exists()) // TODO: inMsg now contains overwrite flag; add support for the flag
              didExist = true
            else
              parsedFileOutputPath.getParent.toFile.mkdirs()
            val outWriter = new FileWriter(parsedFileOutputPath.toFile)
            val extBackend: parsers.ParserBackendExternal = new parsers.JsonWriterBackend(optimizer.parseableMetaInfo, outWriter)
            try {
              pResult=  optimizer.parseExternal(inMsg.mainFileUri,filePath.toString,extBackend,parser.name)
            } catch {
              case NonFatal(e) => errorMessage = Some(s"${parser.name} threw: $e when parsing $filePath")
            } finally {
              optimizer.cleanup()
              outWriter.flush()
              outWriter.close()
            }
            println(s"ParserResult: $pResult when parsing $filePath by ${parser.name}" )
            pResult match {
              case ParseSuccess | ParseWithWarnings =>
                parsedFileUri = Some(pFURI)
                parsedFilePath = Some(parsedFileOutputPath.toString)
              case _ => ()
            }
          case None =>
            errorMessage = Some("Error uncompressing main file")
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
}
