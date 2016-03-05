package eu.nomad_lab.parsing_queue

import java.io._
import java.nio.file.{Path, Paths}

import com.typesafe.scalalogging.StrictLogging
import eu.nomad_lab.parsers.ParserCollection
import eu.nomad_lab.{TreeType, JsonSupport}
import eu.nomad_lab.QueueMessage.{CalculationParserRequest, TreeParserRequest}
import eu.nomad_lab.parsing_queue.TreeParser.TreeParserException
import org.apache.commons.compress.archivers.{ArchiveStreamFactory, ArchiveInputStream}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipFile}
import org.apache.tika.Tika

import scala.annotation.tailrec
import scala.collection.mutable


object TreeParser{
  class TreeParserException(
                            message: TreeParserRequest,
                            msg: String,
                            what: Throwable = null
                            ) extends Exception(
    s"$msg when parsing ${JsonSupport.writeStr(message)} ",
    what
  )

  val tika = new Tika()
  val archieveStreamFactory = new ArchiveStreamFactory()
}
class TreeParser(
                  val parserCollection: ParserCollection
                ) extends StrictLogging {

  /** Find the parsable files and parsers. Return the list of messages
    *
    * */
  def findParser(incomingMessage : TreeParserRequest ) ={
    //Read file and get the corresponding parsers
    var msgList: scala.collection.mutable.MutableList[CalculationParserRequest] = mutable.MutableList()
    val f = new File(incomingMessage.treeFilePath)
    if (!f.isFile)
      logger.error(f + " doesn't exist")
    else if (f.isDirectory)
      logger.error(f + "is a directory") //TODO: Handle directories
    else {
      //    To infer the type of the file before hand, in case not passed to the tree parser; At the moment the type has been fixed; Only Tar and Zip are handled correctly
      val treeType: TreeType.Value = incomingMessage.treeType match {
        case TreeType.Unknown =>
          val tempfis:InputStream = new BufferedInputStream(new FileInputStream(f))
          val buf = Array.fill[Byte](1024)(0)
          val nRead = parserCollection.tryRead(tempfis, buf, 0)
          val minBuf = buf.dropRight(buf.size - nRead)
          val mimeType: String = TreeParser.tika.detect(minBuf, f.getName)
          logger.info(s"mimeType: $mimeType")
          tempfis.close()
          mimeType match {
            case "application/zip"  => TreeType.Zip
            case "application/x-tar" => TreeType.Tar
            case _ => TreeType.Unknown
          }
        case v => v
      }
      logger.info(s"incomingMessage: ${JsonSupport.writePrettyStr(incomingMessage)}, treeType: $treeType")
      val scannedFiles:Map[String,String] = treeType match {
        case TreeType.Zip =>  scanZipFile(incomingMessage)
        case TreeType.Tar =>
          val bis:InputStream = new BufferedInputStream(new FileInputStream(f))
          val ais: ArchiveInputStream =  TreeParser.archieveStreamFactory.createArchiveInputStream(bis)
          val filesToUncompress:Map[String, String] = Map()
          val files = scanArchivedInputStream(filesToUncompress,ais)
          ais.close()
          bis.close()
          files
        case _ => Map()
      }
      logger.info("All extracted files: " + scannedFiles )
      for( (filePath,parser) <- scannedFiles ) {
        val partialFilePath = incomingMessage.relativeTreeFilePath match {
          case Some(rPath) =>
            if (filePath.startsWith(rPath))
              filePath.drop(rPath.size)
            else
              filePath
          case None =>
            filePath
        }
        val nUri = if (incomingMessage.treeUri.endsWith("/"))
          incomingMessage.treeUri.dropRight(1)
        else
          incomingMessage.treeUri
        val mainFileUri = nUri + "/" + partialFilePath.dropWhile(_ == '/')
        val message = CalculationParserRequest(
          parserName = parser,
          mainFileUri = mainFileUri,
          relativeFilePath = filePath,
          treeFilePath = incomingMessage.treeFilePath,
          treeType = treeType
        )
        msgList += message
      }
    }
    msgList
  }

  /** Scan a zip file and if possible, find the most appropriate parser for each file in the zip archive
    *
    * */
  def scanZipFile(incomingMessage: TreeParserRequest): Map[String,String] = {
    incomingMessage.treeType match {
      case TreeType.Zip =>
      var fileParserName: Map[String,String] = Map()
      val zipFile = new ZipFile(incomingMessage.treeFilePath)
      val entries = zipFile.getEntries()
      while (entries.hasMoreElements()) {
        val zipEntry: ZipArchiveEntry = entries.nextElement()
        if (!zipEntry.isDirectory && !zipEntry.isUnixSymlink) {
          //Only check non directory and symlink for now; TODO: Add support to read symlink
          val zIn: InputStream = zipFile.getInputStream(zipEntry)
          val buf = Array.fill[Byte](8*1024)(0)
          val nRead = parserCollection.tryRead(zIn, buf, 0)
          val minBuf = buf.dropRight(buf.size - nRead)
          val candidateParsers = parserCollection.scanFile(zipEntry.getName, minBuf).sorted
          if (candidateParsers.nonEmpty)
          {
            fileParserName += (zipEntry.getName -> candidateParsers.head.parserName)
            logger.info(s"${zipEntry.getName} -> ${candidateParsers.head.parserName}")
          }
        }
      }
      fileParserName
      case _ => throw new TreeParserException(incomingMessage, s"Type ${incomingMessage.treeType} is not supported by method scanZipFile" )
    }
  }

  @tailrec final def scanArchivedInputStream(filesToUncompress:Map[String, String], ais:ArchiveInputStream):Map[String, String] = {
    var filesToUC =  filesToUncompress
    Option(ais.getNextEntry) match {
      case Some(ae) =>
        if(ae.isDirectory){
          logger.info(ae.getName + ". It is a directory. Skipping it. Its children will be handled automatically in the recursion")
        }
        else {
          val buf = Array.fill[Byte](8*1024)(0)
          val nRead = parserCollection.tryRead(ais, buf, 0)
          val minBuf = buf.dropRight(buf.size - nRead)
          val candidateParsers = parserCollection.scanFile(ae.getName,minBuf).sorted
          //          logger.info("Candidate Parsers:" + candidateParsers)
          if(candidateParsers.nonEmpty)
            filesToUC += (ae.getName->candidateParsers.head.parserName)
        }
        scanArchivedInputStream(filesToUC,ais)
      case _ =>
        filesToUncompress
    }
  }
}

