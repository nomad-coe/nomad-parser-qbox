package eu.nomad_lab;

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.DefaultFormats

object JsonUtils {

  private final val newlineRe = "\n".r;
  /** Adds the given amount of extra indent at the beginning of each line
    */
  def extraIndenter(dumper: String => Unit, extraIndent: Int): String => Unit = {
    val indent: String = "\n" + (" " * extraIndent);
    { s: String =>
      // dumper(s.replace("\n", indent))
      var i0 = 0
      for (m <- newlineRe.findAllMatchIn(s)) {
        dumper(s.slice(i0, m.start))
        dumper(indent)
        i0 = m.end
      }
      dumper(s.slice(i0, s.length))
    }
  }

  private final val toEscapeRe = "\\\\|\"".r;
  /** Dumps the given string escaping \ and "
    */
  def dumpString(dumper: String => Unit, s: String): Unit = {
    dumper("\"")
    var i0 = 0
    for (m <- toEscapeRe.findAllMatchIn(s)) {
      dumper(s.slice(i0, m.start))
      dumper("\\")
      dumper(m.matched)
      i0 = m.end
    }
    dumper(s.slice(i0, s.length))
    dumper("\"")
  }

  /** Dumps an compact ordered json
    * 
    * Object keys are alphabetically ordered
    */
  def jsonCompactDump(dumper: String => Unit, obj: JValue): Unit = {
    obj match {
      case JString(s) =>
        dumpString(dumper, s)
      case JNothing =>
        ()
      case JDouble(num) =>
        dumper(num.toString)
      case JDecimal(num) =>
        dumper(num.toString)
      case JInt(num) =>
        dumper(num.toString)
      case JBool(value) =>
        if (value)
          dumper("true")
        else
          dumper("false")
      case JNull =>
        dumper("null")
      case JObject(obj) =>
        dumper("{")
        val iter = obj.toArray.sortBy(_._1).iterator
        while (iter.hasNext) {
          val (key, value) = iter.next
          if (value != JNothing) {
            dumpString(dumper,key)
            dumper(":")
            jsonCompactDump(dumper, value)
            while (iter.hasNext) {
              val (key2, value2) = iter.next
              if (value2 != JNothing) {
                dumper(",")
                dumpString(dumper, key2)
                dumper(":")
                jsonCompactDump(dumper, value2)
              }
            }
          }
        }
        dumper("}")
      case JArray(arr) =>
        dumper("[");
        val iter = arr.iterator
        while (iter.hasNext) {
          val value = iter.next
          if (value != JNothing) {
            jsonCompactDump(dumper, value)
            while (iter.hasNext) {
              val value2 = iter.next
              if (value2 != JNothing) {
                dumper(",")
                jsonCompactDump(dumper, value2)
              }
            }
          }
        }
        dumper("]")
    }
  }

  /** returns a string with a compact sorted json representation
    */
  def jsonCompactStr(value: JValue): String = {
    val sb = new StringBuilder();
    jsonCompactDump(sb.append(_), value)
    sb.result()
  }

  /** calculates a measure of the complexity (size) of the json
    */
  def jsonComplexity(value: JValue): Int = value match {
    case JNothing => 1
    case JNull => 1
    case JBool(_) => 1
    case JDouble(_) => 1
    case JDecimal(_) => 1
    case JInt(_) => 1
    //case JLong(_) => 1
    case JString(_) => 1
    case JArray(arr) =>
      arr.foldLeft(1)(_ + jsonComplexity(_))
    case JObject(obj) =>
      obj.foldLeft(1){ (i : Int, value: JField) =>
        value match {
          case JField(k, v) => i + jsonComplexity(v)
        }
      }
  }

}
