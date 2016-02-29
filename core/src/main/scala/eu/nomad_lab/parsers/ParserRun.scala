package eu.nomad_lab.parsers

import java.nio.file.Paths
import eu.nomad_lab.parsers
import scala.collection._
import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.StrictLogging

object ParserRun extends StrictLogging {

  /* Parse the given file path with the given parser and options
  *
  * */
  def parse(parserGen: SimpleExternalParserGenerator,pPath:String,opt:String): ParseResult.Value =
  {
    object BackendType extends Enumeration {
      type Enum = Value
      val JsonWriter, JsonEventEmitter, Netcdf = Value
    }
    val p = Paths.get(pPath)
    val path = (if (!p.toFile().exists()) {
      //val basePath = Paths.get((new java.io.File(".")).getCanonicalPath())
      //val relPath = p.subpath(2, p.getNameCount()).toString()
      //println(s"XXX $basePath + $relPath")
      //basePath.resolve(relPath)
      p.subpath(2, p.getNameCount())
    } else
      p
    ).toAbsolutePath().toString()
    val uri = p.toUri.toString
    val tmpFile = java.io.File.createTempFile("parserTest", ".log")
    val outF = new java.io.FileWriter(tmpFile, true)
    val parser = parserGen.optimizedParser(Seq())
    var backendType: BackendType.Enum = BackendType.JsonEventEmitter
    opt match {
      case "json" =>
      backendType = BackendType.JsonWriter
      case "hdf5" | "netcdf" =>
      backendType = BackendType.Netcdf
      case _ =>
      backendType = BackendType.JsonEventEmitter
    }
    val extBackend: parsers.ParserBackendExternal = backendType match {
      case BackendType.JsonWriter =>
        new parsers.JsonWriterBackend(parser.parseableMetaInfo, outF)
      case BackendType.Netcdf => null
      case BackendType.JsonEventEmitter =>
        new parsers.JsonParseEventsWriterBackend(parser.parseableMetaInfo, outF)
    }
    val res = parser.parseExternal(uri,path,extBackend, parserGen.name)
    outF.close()
    if (res == ParseResult.ParseSuccess) {
      tmpFile.delete()
      parser.cleanup()
    } else {
      logger.warn(s"parsing failure, leaving parsing output in $tmpFile and avoiding parser cleanup")
    }
    res
  }
}
