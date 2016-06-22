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
  mainFileTypes = Seq("application/xml"),
  mainFileRe = """<\?xml version="1.0" encoding="UTF-8"\?>\s*
\s*<fpmd:simulation xmlns:fpmd="http://www.quantum-simulation.org/ns/fpmd/fpmd-1.0">\s*
\s*
\s*=+\s*
\s*I\s*qbox\s*(?<version>[-_.0-9A-Za-z]+).*I
(?:\s*I.+I
)*\s*I\s+http://qboxcode.org\s*I
(?:\s*I.+I
)*\s*=+\s*
""".r,
  cmd = Seq(DefaultPythonInterpreter.python2Exe(), "${envDir}/parsers/qbox/parser/parser-qbox/QboxParser.py",
    "--uri", "${mainFileUri}", "${mainFilePath}"),
  resList = Seq(
    "parser-qbox/QboxParser.py",
    "parser-qbox/QboxCommon.py",
    "parser-qbox/QboxXMLParser.py",
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
