package eu.nomad_lab;
import scala.collection.mutable.ListBuffer
import scala.collection.breakOut
import scala.collection.mutable
//import eu.nomad_lab.meta
//import eu.nomad_lab.parsers

object CLITool {
  /** Really tries to read the whole buffer (from offest on) into buf
    *
    * gives up only if there is an error or EOF
    */
  def tryRead(fIn: java.io.InputStream, buf: Array[Byte], offset: Int = 0): Int = {
    var pos: Int = offset
    var readMore: Boolean = true
    while (readMore) {
      val nReadNow = fIn.read(buf, pos, buf.size - pos)
      if (nReadNow <= 0)
        readMore = false
      else {
        pos += nReadNow
        if (pos >= buf.size)
          readMore = false
      }
    }
    pos - offset
  }

  /** Backend types recognized by this tool
    */
  object BackendType extends Enumeration {
    type Enum = Value
    val JsonWriter, JsonEventEmitter, Netcdf = Value
  }

  val usage = """
Usage:
  nomadTool [--help]
    [--main-meta-file <path to nomadmetainfo.json to load>]
    [--meta-dot-file <where to output dot file>]
    [--main-file <main file to parse>]
    [--parser <parser to use>]
    [--list-parsers]
    [--tree-to-parse <path to parse as tree>]
    [--json]
    [--hdf5]
    [--netcdf]
    [--json-events]

Runs the main parsing step
  """
  def main(args: Array[String]): Unit = {
    if (args.length == 0) {
      println(usage)
      return
    }
    var list: List[String] = args.toList
    var metaInfoDotFile: Option[String] = None
    val classLoader: ClassLoader = getClass().getClassLoader();
    var metaInfoPath = classLoader.getResource("nomad-meta-info/nomad_meta_info/main.nomadmetainfo.json").getFile()
    var mainFile: Option[String] = None
    var treeToParse: Option[String] = None
    var parsersToUse: ListBuffer[String] = ListBuffer()
    var verbose: Boolean = false
    var listParsers: Boolean = false
    var backendType: BackendType.Enum = BackendType.JsonEventEmitter
    while (!list.isEmpty) {
      val arg = list.head
      list = list.tail
      arg match {
        case "--help" | "-h" =>
          println(usage)
          return
        case "--main-meta-file" =>
          if (list.isEmpty) {
            println("Error: missing main meta file after --main-meta-file. $usage")
            return
          }
          metaInfoPath = list.head
          list = list.tail
        case "--meta-dot-file" =>
          if (list.isEmpty) {
            println("Error: missing place to put the dot file for the meta information after --meta-dot-file. $usage")
            return
          }
          metaInfoDotFile = Some(list.head)
          list = list.tail
        case "--main-file" =>
          if (list.isEmpty) {
            println("Error: missing main meta file. $usage")
            return
          }
          mainFile = Some(list.head)
          list = list.tail
        case "--tree-to-parse" =>
          if (list.isEmpty) {
            println("Error: missing tree to parse after --tree-to-parse. $usage")
            return
          }
          treeToParse = Some(list.head)
          list = list.tail
        case "--parser" =>
          if (list.isEmpty) {
            println("Error: missing parser name after --parser. $usage")
            return
          }
          parsersToUse.append(list.head)
          list = list.tail
        case "--verbose" =>
          verbose = true
        case "--list-parsers" =>
          listParsers = true
        case "--json" =>
          backendType = BackendType.JsonWriter
        case "--hdf5" | "--netcdf" =>
          backendType = BackendType.Netcdf
        case "--json-events" =>
          backendType = BackendType.JsonEventEmitter
        case _ =>
          println("Error: unexpected argument $arg. $usage")
          return
      }
    }
    metaInfoDotFile match {
      case Some(outPath) =>
        val resolver = new meta.RelativeDependencyResolver
        val mainEnv = meta.SimpleMetaInfoEnv.fromFilePath(metaInfoPath, resolver)
        val w = new java.io.FileWriter(outPath)
        mainEnv.writeDot(w)
        w.close()
      case None => ()
    }
    val parserCollection = if (parsersToUse.isEmpty) {
      parsers.AllParsers.defaultParserCollection
    } else {
      new parsers.ParserCollection(parsersToUse.map { (parserName: String) =>
        parserName -> parsers.AllParsers.knownParsers(parserName)
      }(breakOut): Map[String, parsers.ParserGenerator])
    }
    if (listParsers) {
      for ( (name, parser) <- parsers.AllParsers.knownParsers) {
        val dot = if (parsers.AllParsers.defaultParserCollection.parsers.contains(name))
          "+"
        else
          "-"
        println(s"$dot $name")
        if (verbose) {
          print("  ")
          println(JsonUtils.prettyStr(parser.parserInfo, extraIndent = 2))
        }
      }
    }
    val backend: parsers.ParserBackendInternal = backendType match {
      case BackendType.JsonWriter => null
      case BackendType.Netcdf => null
      case BackendType.JsonEventEmitter => null
    }

    mainFile match {
      case Some(path) =>
        val cachedOptimizedParsers: mutable.Map[String, parsers.OptimizedParser] = mutable.Map()
        val fIn = new java.io.FileInputStream(path)
        val buf = Array.fill[Byte](8*1024)(0)
        val nRead = tryRead(fIn, buf, 0)
        val minBuf = buf.dropRight(buf.size - nRead)
        val possibleParsers = parserCollection.scanFile(path, minBuf).sorted
        for (parsers.CandidateParser(parserMatch, parserName, parser) <- possibleParsers) {
          val optimizedParser: parsers.OptimizedParser= {
            cachedOptimizedParsers.get(parserName) match {
              case Some(oParser) =>
                oParser
              case None =>
                val oParser = parser.optimizedParser(include = Seq(), exclude = Seq())
                cachedOptimizedParsers += (parserName -> oParser)
                oParser
            }
          }
          val parsingStatus = optimizedParser.parseInternal(path, backend, parserName)
          parsingStatus match {
            case _ => ()
          }
        }
      case None => ()
    }
    treeToParse match {
      case Some(tree) => throw new Exception("to do")
      case None => ()
    }
  }

}
