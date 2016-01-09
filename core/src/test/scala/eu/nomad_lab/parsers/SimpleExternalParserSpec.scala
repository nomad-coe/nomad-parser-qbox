package eu.nomad_lab.parsers

import org.specs2.mutable.Specification
import org.{json4s => jn}
import eu.nomad_lab.meta
import scala.collection.mutable
import java.nio.charset.StandardCharsets

class ParseAndCollect(
  val optimizedParser: SimpleExternalParser,
  val mainFileUri: String,
  val mainFilePath: String,
  val parserName: Option[String] = None
) {
  val metaInfoEnv = optimizedParser.parseableMetaInfo
  val events = mutable.ListBuffer[ParseEvent]()
  val eventStream = new ParseEventsEmitter(
    metaInfoEnv = meta.KnownMetaInfoEnvs.last,
    mainEventDigester = { events += _ },
    startStopDigester = { events += _ })
  val stdErrLines = mutable.ListBuffer[String]()

  def stdErrCollector(fIn: java.io.InputStream): Unit = {
    val lineReader = new java.io.LineNumberReader(new java.io.InputStreamReader(new java.io.BufferedInputStream(fIn), StandardCharsets.UTF_8))
    var hasLines: Boolean = true;
    while (hasLines) {
      val line = lineReader.readLine()
      if (line == null)
        hasLines = false
      else
        stdErrLines += line
    }
  }

  val oldHandler = optimizedParser.stdErrHandler
  optimizedParser.stdErrHandler = Some(stdErrCollector _)
  val parseResult = optimizedParser.parseExternal(
    mainFileUri = mainFileUri,
    mainFilePath = mainFilePath,
    backend = eventStream,
    parserName = parserName match {
      case Some(name) => name
      case None => optimizedParser.parserGenerator.name
    })
  optimizedParser.stdErrHandler = oldHandler
}

/** Specification (fixed tests) for SimpleExternalParser
  */
class SimpleExternalParserSpec extends Specification {
  sequential
  "makeReplacements" >> {
    SimpleExternalParserGenerator.makeReplacements(Map(
      ("a", "AX"),
      ("b", "BX")), "${a}xy$a${c}{\\${a}${b}") must_== "AXxy$a${c}{\\AXBX"
  }

  "testParser1" >> {
    val testParserGen1 = new SimpleExternalParserGenerator(
      name = "testParser1",
      parserInfo = jn.JObject(
        ("name" -> jn.JString("testParser")) ::
          ("version" -> jn.JString("1.0")) :: Nil),
      mainFileTypes = Seq("text/.*"),
      mainFileRe = """XXX testParser1 XXX""".r,
      cmd = Seq("/bin/sh", "${envDir}/pippo/xx/listAll.sh", "${envDir}"),
      resList = Seq(
        "testParser1/pippo/listAll.sh",
        "testParser1/pippo/testParser1-1.sample",
        "nomad_meta_info/meta_types.nomadmetainfo.json",
        "nomad_meta_info/common.nomadmetainfo.json"),
      dirMap = Map(
        "testParser1/pippo" -> "pippo/xx",
        "nomad_meta_info" -> "nomad-meta-info/meta_info/nomad_meta_into")
    )

    "envSetup" >> {
      val envF = testParserGen1.envDir.toFile()
      envF.isDirectory() must_== true
    }
    "testParsing1" >> {
      val testParser1 = testParserGen1.optimizedParser(Seq()) match {
        case p : SimpleExternalParser => p
      }
      val sampleFile: String = "${envDir}/pippo/xx/testParser1-1.sample"
      val res = new ParseAndCollect(testParser1, "file://" + sampleFile, mainFilePath = sampleFile)
      testParser1.cleanup()
      res.parseResult must_== ParseResult.ParseSuccess
      res.events.length must_== 2
    }
    step{
      testParserGen1.cleanup()
    }
  }

  "echoParser" >> {
    val echoParserGen = new SimpleExternalParserGenerator(
      name = "echoParser",
      parserInfo = jn.JObject(
        ("name" -> jn.JString("echoParser")) ::
          ("version" -> jn.JString("1.0")) :: Nil),
      mainFileTypes = Seq("text/.*"),
      mainFileRe = """XXX testParser1 XXX""".r,
      cmd = Seq("/bin/cat", "${mainFilePath}"),
      resList = Seq(
        "testEventStreams/testOut0.json",
        "testEventStreams/testOut1-openSection.json",
        "testEventStreams/testOut2-sectionAndVal.json")
    )

    "countEvents" >> {
      val testParser1 = echoParserGen.optimizedParser(Seq()) match {
        case p: SimpleExternalParser => p
      }
      val sampleFile0: String = "${envDir}/testEventStreams/testOut0.json"
      val sampleFile1: String = "${envDir}/testEventStreams/testOut1-openSection.json"
      val sampleFile2: String = "${envDir}/testEventStreams/testOut2-sectionAndVal.json"
      val samples: Seq[(String, Int)] = Seq(
        (sampleFile0, 2),
        (sampleFile1, 4),
        (sampleFile2, 5))
      examplesBlock {
        for (((sampleFile, nEvents),i) <- samples.zipWithIndex) {
          "sample " + i in {
            val res = new ParseAndCollect(
              testParser1,
              mainFileUri = "file://" + sampleFile,
              mainFilePath = sampleFile
            )
            res.parseResult must_== ParseResult.ParseSuccess
            res.events.length must_== nEvents
          }
        }
      }
      step {
        testParser1.cleanup()
      }
    }
    step {
      echoParserGen.cleanup()
    }
  }
}
