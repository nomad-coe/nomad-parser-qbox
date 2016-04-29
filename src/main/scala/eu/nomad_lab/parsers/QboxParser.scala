package eu.nomad_lab.parsers

import eu.{ nomad_lab => lab }
import eu.nomad_lab.DefaultPythonInterpreter
import org.{ json4s => jn }
import scala.collection.breakOut

object QboxParser extends SimpleExternalParserGenerator(
  name = "QboxParser",
  parserInfo = jn.JObject(
    ("name" -> jn.JString("QboxParser")) ::
      ("parserId" -> jn.JString("QboxParser" + lab.QboxVersionInfo.version)) ::
      ("versionInfo" -> jn.JObject(
        ("nomadCoreVersion" -> jn.JObject(lab.NomadCoreVersionInfo.toMap.map {
          case (k, v) => k -> jn.JString(v.toString)
        }(breakOut): List[(String, jn.JString)])) ::
          (lab.QboxVersionInfo.toMap.map {
            case (key, value) =>
              (key -> jn.JString(value.toString))
          }(breakOut): List[(String, jn.JString)])
      )) :: Nil
  ),
  mainFileTypes = Seq("text/.*"),
  mainFileRe = """\s\|\s*CCC\s*AA\s*SSS\s*TTTTT\s*EEEEE\s*PPPP\s*\|\s*""".r,
  cmd = Seq(DefaultPythonInterpreter.python2Exe(), "${envDir}/parsers/qbox/parser/parser-qbox/QboxParser.py",
    "--uri", "${mainFileUri}", "${mainFilePath}"),
  resList = Seq(
    "parser-qbox/QboxParser.py",
    "parser-qbox/QboxBandParser.py",
    "parser-qbox/QboxCellParser.py",
    "parser-qbox/QboxCommon.py",
    "parser-qbox/setup_paths.py",
    "nomad_meta_info/public.nomadmetainfo.json",
    "nomad_meta_info/common.nomadmetainfo.json",
    "nomad_meta_info/meta_types.nomadmetainfo.json",
    "nomad_meta_info/qbox.nomadmetainfo.json"
  ) ++ DefaultPythonInterpreter.commonFiles(),
  dirMap = Map(
    "parser-qbox" -> "parsers/qbox/parser/parser-qbox",
    "nomad_meta_info" -> "nomad-meta-info/meta_info/nomad_meta_info"
  ) ++ DefaultPythonInterpreter.commonDirMapping()
)
