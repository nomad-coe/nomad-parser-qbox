package eu.nomad_lab.parsing_queue

import java.io._
import java.nio.file.{Path, Paths}

import com.typesafe.scalalogging.StrictLogging
import eu.nomad_lab.QueueMessage.{CalculationParserQueueMessage, ToBeNormalizedQueueMessage}
import eu.nomad_lab.{JsonSupport, CompactSha, TreeType, parsers}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipFile}
import org.apache.commons.compress.utils.IOUtils

object CalculationParser{
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
              destination.getParentFile.mkdirs()
              val zIn: InputStream = zipFile.getInputStream(zipEntry)
              val out: OutputStream = new FileOutputStream(destination)
              IOUtils.copy(zIn, out)
              IOUtils.closeQuietly(zIn)
              out.close()
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
  def uncompressAndInitializeParser(message: CalculationParserQueueMessage): ToBeNormalizedQueueMessage = {
    val parser = parserCollection.parsers(message.parserName)
    CalculationParser.uncompress(message,Paths.get(ucRoot)) match {
      case Some(filePath) =>
        val optimizer = parser.optimizedParser(Seq())
        val uncompressedRoot = new File(ucRoot)
        val cSha = CompactSha()
        cSha.updateStr(message.mainFileUri)
        val mfileUriSha = cSha.gidStr("P")
        val pFURI = "nmd://" +  mfileUriSha
        val parsedFileOutputPath = Paths.get(parsedRoot,mfileUriSha.substring(0,3), mfileUriSha + ".json")
        parsedFileOutputPath.getParent.toFile.mkdirs()
        val parsedFileOutput = parsedFileOutputPath.toFile
        val outWriter = new FileWriter(parsedFileOutput)
        val extBackend: parsers.ParserBackendExternal = new parsers.JsonWriterBackend(optimizer.parseableMetaInfo, outWriter)
        optimizer.parseExternal(message.mainFileUri,filePath.toString,extBackend,parser.name)
        optimizer.cleanup()
        outWriter.flush()
        outWriter.close()
        ToBeNormalizedQueueMessage(
          parserInfo = parser.parserInfo,
          mainFileUri =message.mainFileUri,
          parsedFileUri = pFURI,
          parsedFilePath = parsedFileOutputPath.toString
        )
        case None => throw new CalculationParser.CalculationParserException(message,this, "Error uncompressing main file")
    }
  }
}