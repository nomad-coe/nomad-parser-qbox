package eu.nomad_lab.parsing_queue

import java.io._
import java.nio.file.{Path, Paths}

import com.typesafe.scalalogging.StrictLogging
import eu.nomad_lab.QueueMessage.{CalculationParserResultMessage, CalculationParserQueueMessage, ToBeNormalizedQueueMessage}
import eu.nomad_lab.parsers.ParseResult
import eu.nomad_lab.parsers.ParseResult._
import eu.nomad_lab.{JsonSupport, CompactSha, TreeType, parsers}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipFile}
import org.apache.commons.compress.utils.IOUtils
import org.json4s._

import scala.util.control.NonFatal

object CalculationParser extends StrictLogging {
  class CalculationParserException(
                                    message: CalculationParserQueueMessage,
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
  def uncompress(inMsg: CalculationParserQueueMessage, ucRoot:Path):Option[Path] = {
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
                        val parserCollection: parsers.ParserCollection
                        ) extends  StrictLogging {

  def uncompressAndInitializeParser(inMsg: CalculationParserQueueMessage): ToBeNormalizedQueueMessage = {
    var didExist = false
    var created = true
    var errorMessage:Option[String] = None
    val parser = parserCollection.parsers(inMsg.parserName)
    CalculationParser.uncompress(inMsg,Paths.get(ucRoot)) match {
      case Some(filePath) =>
        val optimizer = parser.optimizedParser(Seq())
        val uncompressedRoot = new File(ucRoot)
        val cSha = CompactSha()
        cSha.updateStr(inMsg.mainFileUri)
        val mfileUriSha = cSha.gidStr("P")
        val pFURI = "nmd://" +  mfileUriSha
        val parsedFileOutputPath = Paths.get(parsedRoot,mfileUriSha.substring(0,3), mfileUriSha + ".json")
        if(parsedFileOutputPath.toFile.exists()) // TODO: inMsg now contains overwrite flag; add support for the flag
          didExist = true
        else
          parsedFileOutputPath.getParent.toFile.mkdirs()
        val outWriter = new FileWriter(parsedFileOutputPath.toFile)
        val extBackend: parsers.ParserBackendExternal = new parsers.JsonWriterBackend(optimizer.parseableMetaInfo, outWriter)
        var pResult:ParseResult = ParseSuccess
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
        var parsedFileUri:Option[String] = None
        var parsedFilePath:Option[String] = None
        pResult match {
          case ParseSuccess | ParseWithWarnings =>
            parsedFileUri = Some(pFURI)
            parsedFilePath = Some(parsedFileOutputPath.toString)
          case _ =>
        }

        val calculationParserResultMessage = CalculationParserResultMessage(
          parseResult = pResult,
          parserInfo = parser.parserInfo,
          parsedFileUri = parsedFileUri,
          parsedFilePath = parsedFilePath,
          didExist = didExist,
          created = created,
          errorMessage = errorMessage,
          parseRequest = inMsg
        )
//        generateNomarlizerMessage(calculationParserResultMessage)

      //
        ToBeNormalizedQueueMessage(
        parserInfo = parser.parserInfo,
        mainFileUri = inMsg.mainFileUri,
        parsedFileUri = pFURI,
        parsedFilePath = parsedFileOutputPath.toString
        )

        case None => throw new CalculationParser.CalculationParserException(inMsg,this, "Error uncompressing main file")
    }
  }
}