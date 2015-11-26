package eu.nomad_lab.parsers;
import com.typesafe.scalalogging.StrictLogging
import com.typesafe.config.{Config, ConfigFactory}
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.io.FileOutputStream
import java.io.InputStream
import java.io.File
import org.json4s.jackson.JsonMethods
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import eu.nomad_lab.meta
import eu.nomad_lab.JsonSupport.formats
import eu.nomad_lab.JsonUtils
import scala.sys.process.Process
import scala.sys.process.ProcessIO

object SimpleExternalParserGenerator extends StrictLogging {
  /** Exception unpacking things from the resources and setting up environment
    */
  class UnpackEnvException(
    msg: String, what: Throwable = null
  ) extends Exception(msg, what)

  /** Copies the resources files listed in resList to targetDir, renaming their prefixes as
    * defined in dirMap.
    */
  def setupEnv(dirMap: Map[String, String], resList: Seq[String], targetDir: Path): Unit = {
    val classLoader: ClassLoader = getClass().getClassLoader();
    resList.foreach { (inFilePath: String) =>
      var outFilePath = inFilePath
      var matchLen = 0
      dirMap.foreach { case (inDir, outDir) =>
        if (inFilePath.startsWith(inDir) && inDir.length > matchLen)
          outFilePath = outDir + inFilePath.stripPrefix(inDir)
      }
      outFilePath = outFilePath.stripPrefix("/")
      val outPath = targetDir.resolve(outFilePath)
      val outDir: File = outPath.getParent().toFile()
      if (!outDir.isDirectory() && ! outDir.mkdirs())
        throw new UnpackEnvException(s"Cannot generate directory $outDir")
      val outF = new FileOutputStream(outPath.toFile())
      val inF: java.io.InputStream = classLoader.getResourceAsStream(inFilePath)
      val buffer = Array.fill[Byte](8192)(0)
      var readBytes: Int = inF.read(buffer)
      while (readBytes > 0) {
        outF.write(buffer, 0, readBytes)
        readBytes = inF.read(buffer)
      }
    }
  }
}

class SimpleExternalParserGenerator(
  val name: String,
  val parserInfo: JObject,
  val mainFileTypes: Seq[String],
  val mainFileCheck: (String, Array[Byte], Option[String]) => Option[ParserMatch],
  val dirMap: Map[String, String],
  val resList: Seq[String],
  val cmd: (Path, Path) => Seq[String],
  val baseEnvDir: String,
  val baseTempDir: String,
  metaInfo: Option[meta.MetaInfoEnv] = None,
  val ancillaryFilesPrefilter: AncillaryFilesPrefilter.Value = AncillaryFilesPrefilter.SubtreeDepth1

) extends ParserGenerator {
  val envDir = Files.createTempDirectory(Paths.get(baseTempDir), "parserEnv")

  SimpleExternalParserGenerator.setupEnv(dirMap, resList, envDir)

  /** function that should decide if this main file can be parsed by this parser
    * looking at the first 1024 bytes of it
    */
  def isMainFile(filePath: String, bytePrefix: Array[Byte], stringPrefix: Option[String]): Option[ParserMatch] = {
    mainFileCheck(filePath, bytePrefix, stringPrefix)
  }

  def optimizedParser(optimizations: Seq[MetaInfoOps]): OptimizedParser = {
    val tmpDir = Files.createTempDirectory(Paths.get(baseTempDir), "parserTmp")
    new SimpleExternalParser(this, cmd(envDir, tmpDir) , envDir, tmpDir)
  }

  override val parseableMetaInfo: meta.MetaInfoEnv = {
    metaInfo match {
      case Some (metaI) => metaI
      case None      => meta.KnownMetaInfoEnvs.lastAll
    }
  }
}

object SimpleExternalParser {

  /** Exception parsing the output from the external parser
    */
  class ParseStreamException(
    msg: String, what: Throwable = null
  ) extends Exception(msg, what)

  object JsonScanState extends Enumeration {
    type JsonScanState = Value
    val MetaOrEvents, MetaDict, BetweenObjects, EventDict, EventDictPostEvents, Events, Finished = Value
  }
}

class SimpleExternalParser(
  val parserGenerator: SimpleExternalParserGenerator,
  val cmd: Seq[String],
  val envDir: Path,
  val tmpDir: Path
) extends OptimizedParser with StrictLogging {
  import SimpleExternalParser.ParseStreamException

  def ancillaryFilesPrefilter: AncillaryFilesPrefilter.Value = {
    parserGenerator.ancillaryFilesPrefilter
  }

  def isAncillaryFilePathForMainFilePath(mainFilePath: String, ancillaryFile: String): Boolean = true

  /** parses the file at the given path, calling the internal backend with the parser events
    *
    * parserName is used to identify the parser, mainly for logging/debugging
    */
  def parseInternal(mainFileUri: String, mainFilePath: String, backend: ParserBackendInternal, parserName: String): ParseResult.ParseResult = {
    val externalBackend = new ReindexBackend(backend)
    parseExternal(mainFileUri, mainFilePath, externalBackend, parserName)
  }

  /** parses the file at the given path, calling the external backend with the parser events
    *
    * parserName is used to identify the parser, mainly for logging/debugging
    */
  def parseExternal(mainFileUri: String, mainFilePath: String, backend: ParserBackendExternal, parserName: String): ParseResult.ParseResult = {

    def emitJValue(event: JValue): Unit = {
      ParseEvent.fromJValue(Some(backend), event).emitOnBackend(backend)
    }

    def jsonDecode(inF: java.io.InputStream): Unit = {
      import SimpleExternalParser.JsonScanState._

      val factory = JsonMethods.mapper.getFactory()
      val parser: JsonParser = factory.createParser(inF)
      var token = parser.nextToken()
      var cachedFields: List[(String, JValue)] = Nil
      var mainFileUri: Option[String] = None
      var parserInfo: JValue = JNothing
      var state: JsonScanState = MetaOrEvents
      var metaEnv: meta.MetaInfoEnv = null // meta.defaultMetaEnv
      val inArray: Boolean = (token == JsonToken.START_ARRAY)
      if (inArray)
        token = parser.nextToken()
      if (token != JsonToken.START_OBJECT)
        throw new ParseStreamException(s"Expected a json dictionary, not $token")
      while (state != Finished && token != null) {
        token match {
          case JsonToken.FIELD_NAME =>
            val fieldName = parser.getCurrentName()
            fieldName match {
              case "type" =>
                val typeName: String = parser.nextToken match {
                  case JsonToken.VALUE_STRING =>
                    parser.getText()
                  case t =>
                    throw new ParseStreamException(s"Type should be a string not $t")
                }
                typeName match {
                  case "nomad_parse_events_1_0" =>
                    state match {
                      case MetaOrEvents =>
                        state = EventDict
                      case EventDict | EventDictPostEvents =>
                        ()
                      case _ =>
                        throw new ParseStreamException(s"Unexpected type=nomad_parse_events_1_0 when in state $state")
                    }
                  case "nomad_meta_info_1_0" =>
                    state match {
                      case MetaOrEvents | MetaDict =>
                        state = MetaDict
                      case _ =>
                        throw new ParseStreamException(s"Unexpected type=nomad_meta_info_1_0 when in state $state")
                    }
                    parser.readValueAs(classOf[JValue]) match {
                      case JObject(obj) =>
                        metaEnv = meta.SimpleMetaInfoEnv.fromJValue(
                          JObject(("type" -> JString("nomad_meta_info_1_0")) :: cachedFields ::: obj),
                          name = "parserMetaInfo",
                          source = JObject(("source" -> JString("parseStream"))::Nil),
                          dependencyResolver = new meta.NoDependencyResolver(),
                          keepExistingGidsValues = false,
                          ensureGids = true)
                      case _ =>
                        throw new ParseStreamException(s"Expected a json dictionary with meta info for type= monad_meta_info_1_0")
                    }
                    cachedFields = Nil
                    token = parser.nextToken()
                    token match {
                      case null | JsonToken.END_ARRAY =>
                        state = Finished
                      case JsonToken.START_OBJECT =>
                        state = EventDict
                      case _ =>
                        throw new ParseStreamException(s"Expected a json dictionary with type=nomad_parse_events_1_0 or nothing at all after the meta info, not $token")
                    }
                  case t =>
                    throw new ParseStreamException(s"Expected either nomad_parse_events_1_0 or nomad_meta_info_1_0, not $t")
                }
              case "description" | "metaInfos" | "dependencies" =>
                state match {
                  case MetaOrEvents | MetaDict =>
                    state = MetaDict
                  case _ =>
                    throw new ParseStreamException(s"Unexpected field $fieldName when in state $state")
                }
                cachedFields = (fieldName -> parser.readValueAs(classOf[JValue])) :: cachedFields
              case "mainFileUri" =>
                state match {
                  case MetaOrEvents =>
                    state = EventDict
                  case EventDict | EventDictPostEvents =>
                    ()
                  case _ =>
                    throw new ParseStreamException(s"Unexpected mainFileUri when in state $state")
                }
                parser.readValueAs(classOf[JValue]) match {
                  case JString(str) =>
                    mainFileUri = Some(str)
                  case JNull | JNothing =>
                    ()
                  case value =>
                    throw new ParseStreamException(s"Expected a String as mainFileUri, not $value")
                }
              case "parserInfo" =>
                state match {
                  case MetaOrEvents =>
                    state = EventDict
                  case EventDict | EventDictPostEvents =>
                    ()
                  case _ =>
                    throw new ParseStreamException(s"Unexpected mainFileUri when in state $state")
                }
                parserInfo = parser.readValueAs(classOf[JValue])
              case "events" =>
                state match {
                  case MetaOrEvents | EventDict =>
                    state = Events
                  case _ =>
                    throw new ParseStreamException(s"Unexpected events field when in state $state")
                }
                token = parser.nextToken()
                token match {
                  case JsonToken.START_ARRAY =>
                    ()
                  case _ =>
                    throw new ParseStreamException(s"Expected an array after the events field, not $token")
                }
              case _ =>
                throw new ParseStreamException(s"Unexpected field $fieldName in state $state")
            }
          case JsonToken.END_OBJECT =>
            state match {
              case EventDict =>
                throw new ParseStreamException("Missing events field in nomad_parse_events_1_0")
              case EventDictPostEvents =>
                state = BetweenObjects
              case _ =>
                throw new ParseStreamException(s"Unexpected end of object in state $state")
            }
          case JsonToken.END_ARRAY =>
            state match {
              case BetweenObjects =>
                state = Finished
              case Events =>
                state = EventDictPostEvents
              case _ =>
                throw new ParseStreamException(s"Unexpected end of array in state $state")
            }
          case JsonToken.START_OBJECT =>
            state match {
              case BetweenObjects =>
                state = EventDict
              case Events =>
                val ev = parser.readValueAs(classOf[JValue])
                emitJValue(ev)
              case _ =>
                throw new ParseStreamException(s"Unexpected object start in state $state")
            }
          case null =>
            state match {
              case BetweenObjects =>
                state = Finished
              case _ =>
                throw new ParseStreamException(s"Unexpected end in state $state")
            }
          case _ =>
            throw new ParseStreamException(s"Unexpected token $token in state $state")
        }
      }
      parser.close()
    }

    def sendInput(pIn: java.io.OutputStream): Unit = {
      val out: java.io.Writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(pIn));
      out.write(s"""{
 "mainFile": """)
      JsonUtils.dumpString(mainFilePath, out)
      out.write("\n}\n")
      out.close()
      pIn.close()
    }

    def logErrors(pErr: java.io.InputStream): Unit = {
      val lIn = new java.io.LineNumberReader(new java.io.BufferedReader(new java.io.InputStreamReader(pErr)))
      var line = lIn.readLine()
      while (line != null) {
        logger.warn(s"${lIn.getLineNumber()}: $line")
        line = lIn.readLine()
      }
      lIn.close()
      pErr.close()
    }

    var hadErrors: Boolean = false
    try {
      val proc = Process(cmd).run(new ProcessIO(sendInput _, jsonDecode _, logErrors _))
      // should switch to finite state machine and allow async interaction, for now we just block...
      hadErrors = (proc.exitValue != 0)
    } catch {
      case e: Exception =>
        logger.error(s"Parser $parserName had an exception when parsing $mainFileUri")
        hadErrors = true
    }
    if (!hadErrors)
      ParseResult.ParseSuccess
    else
      ParseResult.ParseFailure
  }
}
