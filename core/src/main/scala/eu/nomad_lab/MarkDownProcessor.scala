package eu.nomad_lab

import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable

//object MarkDownProcessor extends StrictLogging {
object MarkDownProcessor {

  object MarkDownProcessorState extends Enumeration {
    type MarkDownProcessorState = Value
    val BetweenWords, InWord, InNonWord, InDollarMath, InSquareMath,InSquareBracket, InParentesisMath, BackSlash, InTag, InTagString = Value
  }
  lazy val pegDownProcessor = new org.pegdown.PegDownProcessor

  def pegDown(x: String): String = {
    pegDownProcessor.markdownToHtml(x)
  }

  def processMarkDown(text: String, keys: Set[String], keyLinkBuilder: String => String, markDownProcessor: String => String = null): String = {
    import MarkDownProcessorState._
    val escapedText = new mutable.StringBuilder()
    var state = MarkDownProcessorState.BetweenWords
    var i: Int = 0
    var lastEmit: Int = 0
    var wordStart: Int = -1
    var mathStart: Int = -1
    var placeholderNr: Int = 0
    var inBacktick: Boolean = false
    val placeholderValues = mutable.Map[String, String]()

    def addMathPlaceholder(): Unit = {
      escapedText ++= text.substring(lastEmit, mathStart)
      var placeholder = s"(XXX-$placeholderNr-XXX)"
      placeholderNr += 1
      while (text.contains(placeholder)) {
        var placeholder = s"(XXX-$placeholderNr-XXX)"
        placeholderNr += 1
      }
      placeholderValues += (placeholder ->
        text.substring(mathStart, i).replaceAll("&", "&amp;").replaceAll(">", "&gt;").replaceAll("<", "&lt;"))
      escapedText ++= placeholder
      lastEmit = i
      mathStart = -1
    }
    while (i < text.length()) {
      var cAtt = text.charAt(i)
      i += 1
      state match {
        case BetweenWords =>
          cAtt match {
            case '\\' =>
              state = BackSlash
            case '[' =>
              state = InSquareBracket
            case '`' =>
              inBacktick = !inBacktick
            case '$' =>
              state = InDollarMath
              mathStart = i -1
            case '<' =>
              state = InTag
            case '>' =>
              escapedText ++= text.substring(lastEmit, i - 1)
              lastEmit = i
              escapedText ++= "&gt;"
            case _ if Character.isLetter(cAtt) =>
              wordStart = i - 1
              state = InWord
            case _ if (Character.isDigit(cAtt) || cAtt == '_') =>
              state = InNonWord
            case _ => ()
          }
        case InWord =>
          while ((Character.isLetterOrDigit(cAtt) || cAtt == '_' || cAtt == '-') && i < text.length()) {
            cAtt = text.charAt(i)
            i += 1
          }
          val word = text.substring(wordStart, i - 1)
          if (keys(word)) {
            escapedText ++= text.substring(lastEmit, wordStart)
            escapedText ++= keyLinkBuilder(word)
            lastEmit = i - 1
            wordStart = -1
          }
          state = BetweenWords
        case InNonWord =>
          while ((Character.isLetterOrDigit(cAtt) || cAtt == '_') && i < text.length()) {
            cAtt = text.charAt(i)
            i += 1
          }
          state = BetweenWords
        case InDollarMath =>
          var escape: Boolean = false
          var atEnd: Boolean = false
          i -= 1
          while (!atEnd && i < text.length()) {
            cAtt = text.charAt(i)
            i += 1
            if (cAtt == '\\') {
              escape = !escape
            } else if (cAtt == '$') {
              if (!escape)
                atEnd = true
              escape = false
            } else {
              escape = false
            }
          }
          addMathPlaceholder()
          state = BetweenWords
        case InSquareMath =>
          var escape: Boolean = false
          var atEnd: Boolean = false
          i -= 1
          while (!atEnd && i < text.length()) {
            cAtt = text.charAt(i)
            i += 1
            if (cAtt == '\\') {
              escape = !escape
            } else if (cAtt == ']') {
              if (escape)
                atEnd = true
              escape = false
            } else {
              escape = false
            }
          }
          addMathPlaceholder()
          state = BetweenWords
        case InParentesisMath =>
          var escape: Boolean = false
          var atEnd: Boolean = false
          i -= 1
          while (!atEnd && i < text.length()) {
            cAtt = text.charAt(i)
            i += 1
            if (cAtt == '\\') {
              escape = !escape
            } else if (cAtt == ')') {
              if (escape)
                atEnd = true
              escape = false
            } else {
              escape = false
            }
          }
          addMathPlaceholder()
          state = BetweenWords
        case BackSlash =>
          cAtt match {
            case '$' =>
              escapedText ++= text.substring(lastEmit, i - 1)
              escapedText ++= "$\\$$"
              lastEmit = i
            case '(' =>
              if (inBacktick) {
                mathStart = i - 2
                state = InParentesisMath
              }
            case '[' =>
              if (inBacktick) {
                mathStart = i - 2
                state = InSquareMath
              }
            case _ =>
              state = BetweenWords
          }
        case InTag =>
          if (cAtt == '>')
            state = BetweenWords
          else if (cAtt == '"')
            state = InTagString
        case InSquareBracket =>
          if (cAtt == ']')
            state = BetweenWords
        case InTagString =>
          if (cAtt == '\\')
            i += 1
          else if (cAtt == '"')
            state = InTag
      }
    }
    escapedText ++= text.substring(lastEmit)
    val processor: String => String = if(markDownProcessor == null)
      pegDown
    else
      markDownProcessor
    val textWithPlaceholders = escapedText.toString()
//    logger.debug(textWithPlaceholders)
    var processed: String = processor(textWithPlaceholders)
    for ( (placeholder, value) <- placeholderValues) {
      processed = processed.replace(placeholder, value)
    }
    processed
  }

}
