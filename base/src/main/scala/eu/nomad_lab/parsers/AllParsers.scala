package eu.nomad_lab.parsers;
import scala.collection.breakOut

object AllParsers {
  /** All known parsers
    */
  val knownParsers: Map[String, ParserGenerator] = {
    Map()
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
