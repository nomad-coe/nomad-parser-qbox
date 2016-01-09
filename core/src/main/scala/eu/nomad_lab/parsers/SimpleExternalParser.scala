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
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.collection.mutable

object SimpleExternalParserGenerator extends StrictLogging {
  /** Exception unpacking things from the resources and setting up environment
    */
  class UnpackEnvException(
    msg: String, what: Throwable = null
  ) extends Exception(msg, what)

  /** Copies the resources files listed in resList to targetDir, renaming their prefixes as
    * defined in dirMap.
    * returns target dir to use it a single lazy evaluation for envDir
    */
  def copyAndRenameFromResources(resList: Seq[String], targetDir: Path, dirMap: Map[String, String] = Map()): Unit = {
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
      if (inF == null)
        throw new UnpackEnvException(s"failed to get stream for resource at path $inFilePath")
      val buffer = Array.fill[Byte](8192)(0)
      var readBytes: Int = inF.read(buffer)
      while (readBytes > 0) {
        outF.write(buffer, 0, readBytes)
        readBytes = inF.read(buffer)
      }
      logger.debug(s"resCopy: $inFilePath -> $outPath")
    }
  }

  val varToReplaceRe = """\$\{([a-zA-Z][a-zA-Z0-9]*)\}""".r
  /** replaces ${var} expressions that have a var -> value pair in the replacements with value
    */
  def makeReplacements(repl: Map[String, String], str: String): String = {
    var i: Int = 0
    val s = new mutable.StringBuilder()
    for (m <- varToReplaceRe.findAllMatchIn(str)) {
      val varNow = m.group(1)
      repl.get(varNow) match {
        case Some(value) =>
          s ++= str.substring(i, m.start) // would subSequence be more efficient?
          s ++= value
          i = m.end
        case None =>
          ()
      }
    }
    if (i == 0) {
      str
    } else {
      s ++= str.substring(i)
      s.toString()
    }
  }

  /** recursive deletion of files
    */
  def recursiveDelete(directory: java.io.File): Boolean = {
    if (directory.isDirectory()) {
      val files = directory.listFiles()
      if(files != null){
        for (f <- files) {
          if(f.isDirectory())
            recursiveDelete(f)
          else
            f.delete()
        }
      }
    }
    directory.delete()
  }
}

class SimpleExternalParserGenerator(
  val name: String,
  val parserInfo: JObject,
  val mainFileTypes: Seq[String],
  val mainFileRe: Regex,
  val cmd: Seq[String],
  val cmdCwd: String = "${tmpDir}",
  val cmdEnv: Map[String, String] = Map(),
  val resList: Seq[String] = Seq(),
  val dirMap: Map[String, String] = Map(),
  val extraCmdVars: Map[String, String] = Map(),
  val baseEnvDir: String = "/tmp",
  val baseTempDir: String = "/tmp",
  val mainFileMatchPriority: Int = 0,
  val mainFileMatchWeak: Boolean = false,
  val metaInfoEnv: Option[meta.MetaInfoEnv] = None,
  val ancillaryFilesPrefilter: AncillaryFilesPrefilter.Value = AncillaryFilesPrefilter.SubtreeDepth1
) extends ParserGenerator with StrictLogging {

  def setupEnv(): Path = {
    val tDir = Files.createTempDirectory(Paths.get(baseTempDir), "parserEnv")
    try {
      SimpleExternalParserGenerator.copyAndRenameFromResources(resList, tDir, dirMap)
      logger.info(s"Did setup of environment for parser $name in $tDir")
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Failed setup of environment for parser $name in $tDir due to $e")
        throw new SimpleExternalParserGenerator.UnpackEnvException(s"Failed setup of environment for parser $name in $tDir", e)
    }
    tDir
  }

  var _envDir: Path = null
  def envDir: Path = {
    if (_envDir == null)
      _envDir = setupEnv()
    _envDir
  }

  /** function that should decide if this main file can be parsed by this parser
    * looking at the first 1024 bytes of it
    */
  def isMainFile(filePath: String, bytePrefix: Array[Byte], stringPrefix: Option[String]): Option[ParserMatch] = {
    stringPrefix match {
      case Some(str) =>
        mainFileRe.findFirstMatchIn(str) match {
          case Some(m) =>
            Some(ParserMatch(mainFileMatchPriority, mainFileMatchWeak))
          case None =>
            None
        }
      case None =>
        None
    }
  }

  def optimizedParser(optimizations: Seq[MetaInfoOps]): OptimizedParser = {
    val tmpDir = Files.createTempDirectory(Paths.get(baseTempDir), "parserTmp")
    val allReplacements = extraCmdVars +
        ("envDir" -> envDir.toString()) +
        ("tmpDir" -> tmpDir.toString())
    val command = cmd.map{
      SimpleExternalParserGenerator.makeReplacements(allReplacements, _)
    }
    new SimpleExternalParser(
      parserGenerator = this,
      tmpDir = tmpDir)
  }

  override val parseableMetaInfo: meta.MetaInfoEnv = {
    metaInfoEnv match {
      case Some(metaI) => metaI
      case None        => meta.KnownMetaInfoEnvs.all
    }
  }

  /** deletes the environment directory that had been created
    */
  override def cleanup(): Unit = {
    if (_envDir != null)
      SimpleExternalParserGenerator.recursiveDelete(_envDir.toFile())
    // avoid? risks reallocation...
    _envDir = null
  }
}

object ExternalParserWrapper {

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

/** starts an external parser process and sends its output to the backend
  *
  * Should not be restarted.
  * currently one run per file, but contains already embrionic support for multiple
  * file parsing by a single run
  */
class ExternalParserWrapper(
  val backend: ParserBackendExternal,
  val envDir: Path,
  val tmpDir: Path,
  val cmd: Seq[String],
  val cmdCwd: String,
  val cmdEnv: Map[String, String],
  val parserName: String,
  val fixedParserInfo: JValue = JNothing,
  val extraCmdVars: Map[String, String],
  val stdInHandler: Option[java.io.OutputStream => Unit] = None,
  val stdErrHandler: Option[java.io.InputStream => Unit] = None,
  var mainFileUri: Option[String] = None,
  var mainFilePath: Option[String] = None,
  var hadErrors: Boolean = false
) extends StrictLogging {
  var parserInfo: JValue = JNothing
  var parserStatus: Option[ParseResult.Value] = None
  var parserErrors: JValue = JNothing

  /** clears the values used in startedParsingSession/finishedParsingSession
    */
  def clearStartStop(): Unit = {
    mainFileUri = None
    parserInfo = JNothing
    parserStatus = None
    parserErrors = JNothing
    parserInfo = fixedParserInfo
  }

  /** returns a map with all variable replacements
    */
  def allReplacements: Map[String, String] = {
    var res: Map[String, String] = extraCmdVars +
      ("envDir" -> envDir.toString()) +
      ("tmpDir" -> tmpDir.toString()) +
      ("parserName" -> parserName)
    mainFilePath match {
      case Some(path) =>
        res += ("mainFilePath" -> path)
      case None => ()
    }
    mainFileUri match {
      case Some(uri) =>
        res += ("mainFileUri" -> uri)
      case None => ()
    }
    parserInfo match {
      case JNothing => ()
      case _ =>
        res += ("parserInfo" -> JsonUtils.normalizedStr(parserInfo))
    }
    parserStatus match {
      case Some(parseResult) =>
        res += ("parserStatus" -> parseResult.toString())
      case None => ()
    }
    parserErrors match {
      case JNothing => ()
      case _ => ("parserErrors" -> JsonUtils.normalizedStr(parserErrors))
    }
    res
  }

  /** Performs the replacements in allReplacements on the given string
    */
  def makeReplacements(s: String): String = {
    SimpleExternalParserGenerator.makeReplacements(allReplacements, s)
  }

  /** Decodes a json parse event and emits it to the backend
    */
  def emitJValue(event: JValue): Unit = {
    ParseEvent.fromJValue(Some(backend), event).emitOnBackend(backend)
  }

  /** Decodes the stream of json emitted by the parser and forwards events to the backend
    *
    * This function basically decodes the stdout output of an external parser
    * It supports either a single output, or an array with multiple parsing
    * so that starting multiple processes (and the whole initialization) can be
    * skipped
    */
  def jsonDecode(inF: java.io.InputStream): Unit = {
    import ExternalParserWrapper.JsonScanState._
    import ExternalParserWrapper.ParseStreamException

    try {
      val factory = JsonMethods.mapper.getFactory()
      val parser: JsonParser = factory.createParser(inF)
      var token = parser.nextToken()
      var cachedFields: List[(String, JValue)] = Nil
      var state: JsonScanState = MetaOrEvents
      var metaEnv: meta.MetaInfoEnv = null // meta.defaultMetaEnv
      val inArray: Boolean = (token == JsonToken.START_ARRAY)
      if (inArray)
        token = parser.nextToken()
      if (token != JsonToken.START_OBJECT)
        throw new ParseStreamException(s"Expected a json dictionary, not $token")
      while (state != Finished && token != null) {
        token = parser.nextToken()
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
                    token = parser.nextToken()
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
                token = parser.nextToken()
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
                token = parser.nextToken
                parser.readValueAs(classOf[JValue]) match {
                  case JString(str) =>
                    mainFileUri = mainFileUri match {
                      case None => Some(str)
                      case Some(_) => mainFileUri
                    }
                  case JNull | JNothing =>
                    ()
                  case value =>
                    throw new ParseStreamException(s"Expected a String as mainFileUri, not $value")
                }
              case "parserStatus" =>
                state match {
                  case MetaOrEvents =>
                    state = EventDict
                  case EventDict | EventDictPostEvents =>
                    ()
                  case _ =>
                    throw new ParseStreamException(s"Unexpected parserStatus when in state $state")
                }
                token = parser.nextToken()
                parser.readValueAs(classOf[JValue]) match {
                  case JString(str) =>
                    parserStatus = Some(ParseResult.withName(str))
                  case JNull | JNothing =>
                    ()
                  case value =>
                    throw new ParseStreamException(s"Expected a String as parserStatus, not $value")
                }
              case "parserInfo" =>
                state match {
                  case MetaOrEvents =>
                    state = EventDict
                  case EventDict | EventDictPostEvents =>
                    ()
                  case _ =>
                    throw new ParseStreamException(s"Unexpected parserInfo when in state $state")
                }
                token = parser.nextToken()
                val value = parser.readValueAs(classOf[JValue])
                parserInfo = parserInfo match {
                  case JNothing => value
                  case _ => parserInfo
                }
              case "parserErrors" =>
                state match {
                  case MetaOrEvents =>
                    state = EventDict
                  case EventDict | EventDictPostEvents =>
                    ()
                  case _ =>
                    throw new ParseStreamException(s"Unexpected parserErrors when in state $state")
                }
                token = parser.nextToken()
                parserErrors = parser.readValueAs(classOf[JValue])
              case "events" =>
                state match {
                  case MetaOrEvents | EventDict =>
                    state = Events
                  case _ =>
                    throw new ParseStreamException(s"Unexpected events field when in state $state")
                }
                backend.startedParsingSession(this.mainFileUri, this.parserInfo, parserStatus, parserErrors)
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
                backend.finishedParsingSession(parserStatus, parserErrors, this.mainFileUri, this.parserInfo)
                clearStartStop()
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
    } catch {
      case e: Exception =>
        hadErrors = true
        inF.close()
        // log instad of throwing?
        throw new ParseStreamException(s"Error parsing output of parser $parserName (${JsonUtils.prettyStr(parserInfo, 2)}) when parsing $mainFileUri at $mainFilePath", e)
    }
  }

  /** sends the file to parse on stdIn
    *
    *  (embrio support for reusing, and fork avoidance)
    */
  def sendInput(pIn: java.io.OutputStream): Unit = {
    val out: java.io.Writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(pIn));
    out.write(s"""{
 "mainFileUri": """)
    JsonUtils.dumpString(mainFileUri.getOrElse(""), out)
    out.write(s""",
 "mainFile": """)
    JsonUtils.dumpString(mainFilePath.getOrElse(""), out)
    out.write("\n}\n")
    out.close()
    pIn.close()
  }

  /** logs stderr output as warning
    */
  def logErrors(pErr: java.io.InputStream): Unit = {
    val lIn = new java.io.LineNumberReader(new java.io.BufferedReader(new java.io.InputStreamReader(pErr)))
    var line = lIn.readLine()
    while (line != null) {
      logger.warn(s"<$parserName.stderr>:${lIn.getLineNumber()}: $line")
      line = lIn.readLine()
    }
    lIn.close()
    pErr.close()
  }

  /** starts the parser process and forwards output to the backend
    *
    * Returns true if there were no internal errors
    */
  def run(): Boolean = {
    try {
      val command = cmd.map{ makeReplacements _ }
      val cwd = new java.io.File(makeReplacements(cmdCwd))
      val env =  cmdEnv.map{ case (key, value) =>
        key -> SimpleExternalParserGenerator.makeReplacements(allReplacements, value)
      }
      val proc = Process(command, cwd, env.toSeq: _*).run(new ProcessIO(sendInput _, jsonDecode _, logErrors _))
      // should switch to finite state machine and allow async interaction, for now we just block...
      hadErrors = (proc.exitValue != 0)
    } catch {
      case e: Exception =>
        logger.error(s"Parser $parserName had an exception when parsing ${mainFileUri.getOrElse("<unknowUri>")}")
        hadErrors = true
    }
    !hadErrors
  }
}

class SimpleExternalParser(
  val parserGenerator: SimpleExternalParserGenerator,
  val tmpDir: Path,
  var hadErrors: Boolean = false
) extends OptimizedParser with StrictLogging {

  /** returns a map with all variable replacements
    */
  def allReplacements: Map[String, String] = {
    var res: Map[String, String] = parserGenerator.extraCmdVars +
      ("envDir" -> parserGenerator.envDir.toString()) +
      ("tmpDir" -> tmpDir.toString())
    res
  }

  /** Performs the replacements in allReplacements on the given string
    */
  def makeReplacements(s: String): String = {
    SimpleExternalParserGenerator.makeReplacements(allReplacements, s)
  }

  def ancillaryFilesPrefilter: AncillaryFilesPrefilter.Value = {
    parserGenerator.ancillaryFilesPrefilter
  }

  override def parseableMetaInfo: meta.MetaInfoEnv = {
    parserGenerator.parseableMetaInfo
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
    val process = new ExternalParserWrapper(
      mainFileUri = Some(mainFileUri),
      mainFilePath = Some(mainFilePath),
      backend = backend,
      envDir = parserGenerator.envDir,
      tmpDir = tmpDir,
      cmd = parserGenerator.cmd,
      cmdCwd = parserGenerator.cmdCwd,
      cmdEnv = parserGenerator.cmdEnv,
      parserName = parserName,
      fixedParserInfo = JNothing,
      extraCmdVars = parserGenerator.extraCmdVars,
      stdInHandler = None,
      stdErrHandler = None)
    if (process.run())
      ParseResult.ParseSuccess
    else
      ParseResult.ParseFailure
  }

  def cleanup(): Unit = {
    logger.info(s"deleting temporary directory $tmpDir")
    SimpleExternalParserGenerator.recursiveDelete(tmpDir.toFile())
  }
}
