package eu.nomad_lab.parsers

import org.specs2.mutable.Specification

/** Specification (fixed tests) for SimpleExternalParser
  */
class SimpleExternalParserSpec extends Specification {
  "makeReplacements" >> {
    SimpleExternalParserGenerator.makeReplacements(Map(
      ("a", "AX"),
      ("b", "BX")), "${a}xy$a${c}{\\${a}${b}") must_== "AXxy$a${c}{\\AXBX"
  }

}
