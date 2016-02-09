package eu.nomad_lab.parsers
import scala.collection.breakOut

object AllParsers {
  class DuplicateParser(name: String) extends Exception(
    s"Duplicate parser with name $name") { }

  /** All known parsers
    */
  val knownParsers: Map[String, ParserGenerator] = {
    val parserList: Seq[ParserGenerator] = Seq(
      FhiAimsParser,
      CastepParser,
      ExcitingParser,
      GaussianParser,
      DlPolyParser,
      Cp2kParser,
      GpawParser
    )

    val res: Map[String, ParserGenerator] = parserList.map { (pg: ParserGenerator) =>
      pg.name ->  pg
    }(breakOut)

    if (res.size != parserList.length) {
      for (pg <- parserList) {
        if (pg != res(pg.name))
          throw new DuplicateParser(pg.name)
      }
    }
    res
  }

  /** The default active parsers
    */
  val defaultParserCollection: ParserCollection = {
    val parserNames = knownParsers.keys
    new ParserCollection(parserNames.map{ (name: String) =>
      name -> knownParsers(name)
    }(breakOut))
  }
}
