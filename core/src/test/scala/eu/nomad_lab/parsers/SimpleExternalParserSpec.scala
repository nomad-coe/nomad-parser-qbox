package eu.nomad_lab.parsers

import org.specs2.mutable.Specification
import org.{json4s => jn}

/** Specification (fixed tests) for SimpleExternalParser
  */
class SimpleExternalParserSpec extends Specification {
  "makeReplacements" >> {
    SimpleExternalParserGenerator.makeReplacements(Map(
      ("a", "AX"),
      ("b", "BX")), "${a}xy$a${c}{\\${a}${b}") must_== "AXxy$a${c}{\\AXBX"
  }

  val testParser1 = new SimpleExternalParserGenerator(
    name = "testParser1",
    parserInfo = jn.JObject(
      ("name" -> jn.JString("testParser")) ::
        ("version" -> jn.JString("1.0")) :: Nil),
    mainFileTypes = Seq("text/.*"),
    mainFileRe = """XXX testParser1 XXX""".r,
    cmd = Seq("/bin/sh", "${envDir}/pippo/xx/listAll.sh", "${envDir}"),
    resList = Seq(
      "testParser1/pippo/listAll.sh",
      "nomad_meta_info/meta_types.nomadmetainfo.json",
      "nomad_meta_info/common.nomadmetainfo.json"),
    dirMap = Map(
      "testParser1/pippo" -> "pippo/xx",
      "nomad_meta_info" -> "nomad-meta-info/meta_info/nomad_meta_into")
  )

  "testParser1" >> {
    "envSetup" >> {
      val envF = testParser1.envDir.toFile()
      envF.isDirectory() must_== true
    }
  }
}
