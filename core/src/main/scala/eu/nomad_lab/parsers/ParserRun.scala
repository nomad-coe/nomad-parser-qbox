package eu.nomad_lab.parsers

import java.nio.file.Paths
import eu.nomad_lab.parsers
import scala.collection._
import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.StrictLogging

object ParserRun {

  /* Parse the given file path with the given parser and options
  *
  * */
  def parse(Parser:SimpleExternalParserGenerator,pPath:String,opt:String) =
  {
    object BackendType extends Enumeration {
      type Enum = Value
      val JsonWriter, JsonEventEmitter, Netcdf = Value
    }
    val p = Paths.get(pPath).toAbsolutePath
    val path = p.toString
    val uri = p.toUri.toString
    val stdOut = new java.io.OutputStreamWriter(System.out)
    val optimizer = Parser.optimizedParser(Seq())
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
        new parsers.JsonWriterBackend(optimizer.parseableMetaInfo, stdOut)
      case BackendType.Netcdf => null
      case BackendType.JsonEventEmitter =>
        new parsers.JsonParseEventsWriterBackend(optimizer.parseableMetaInfo, stdOut)
    }
    optimizer.parseExternal(uri,path,extBackend,Parser.name)
    optimizer.cleanup()
  }
}
