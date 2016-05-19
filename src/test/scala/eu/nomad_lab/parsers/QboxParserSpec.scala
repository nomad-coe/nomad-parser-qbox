package eu.nomad_lab.parsers

import org.specs2.mutable.Specification

object QboxParserSpec extends Specification {
  "QboxParserTest" >> {
    "test with json-events" >> {
      ParserRun.parse(QboxParser, "parsers/qbox/test/examples/01_h2ogs.r", "json-events") must_== ParseResult.ParseSuccess
    }
    "test with json" >> {
      ParserRun.parse(QboxParser, "parsers/qbox/test/examples/01_h2ogs.r", "json") must_== ParseResult.ParseSuccess
    }
  }
}
