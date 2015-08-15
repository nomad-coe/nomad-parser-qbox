package eu.nomad_lab;

import java.nio.charset.StandardCharsets
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import scala.collection.mutable.ListBuffer

/** methods to handle json, in particular serialize it in a predictable way directly to UTF_8
  *
  * first implementation, could be improved, but worth doing only with real benchmarks
  */
object JsonUtils {

  /** Adds the given amount of extra indent at the beginning of each line
    */
  def extraIndenter(dumper: Array[Byte] => Unit, extraIndent: Int): Array[Byte] => Unit = {
    val indent: Array[Byte] = ("\n" + (" " * extraIndent)).getBytes(StandardCharsets.UTF_8)

    { s: Array[Byte] =>
      // dumper(s.replace("\n", indent))
      var i0 = 0
      var i1 = s.indexOf('\n'.toByte, i0)
      while (i1 >= 0) {
        dumper(s.slice(i0, i1))
        dumper(indent)
        i0 = i1 + 1
        i1 = s.indexOf('\n'.toByte, i0)
      }
      dumper(s.slice(i0, s.length))
    }
  }

  /** Dumps the given UTF_8 encoded string escaping \ and "
    */
  def dumpStringArray(dumper: Array[Byte] => Unit, s: Array[Byte]): Unit = {
    dumper(Array('"'.toByte))
    var i0 = 0
    var j = i0
    while (j < s.length) {
      val c = s(j)
      if (c == '\\'.toByte || c == '"'.toByte) {
        dumper(s.slice(i0, j))
        dumper(Array('\\'.toByte, c))
        i0 = j + 1
      }
      j += 1
    }
    dumper(s.slice(i0, s.length))
    dumper(Array('"'.toByte))
  }

  /** Dumps the given string escaping \ and "
    */
  def dumpString(dumper: Array[Byte] => Unit, s: String): Unit =
    dumpStringArray(dumper, s.getBytes(StandardCharsets.UTF_8))

  /** Dumps an compact ordered json
    * 
    * Object keys are alphabetically ordered
    */
  def jsonCompactDump(dumper: Array[Byte] => Unit, obj: JValue): Unit = {
    obj match {
      case JString(s) =>
        dumpString(dumper, s)
      case JNothing =>
        ()
      case JDouble(num) =>
        dumper(num.toString.getBytes(StandardCharsets.UTF_8))
      case JDecimal(num) =>
        dumper(num.toString.getBytes(StandardCharsets.UTF_8))
      case JInt(num) =>
        dumper(num.toString.getBytes(StandardCharsets.UTF_8))
      case JBool(value) =>
        if (value)
          dumper("true".getBytes(StandardCharsets.UTF_8))
        else
          dumper("false".getBytes(StandardCharsets.UTF_8))
      case JNull =>
        dumper("null".getBytes(StandardCharsets.UTF_8))
      case JObject(obj) =>
        dumper(Array('{'.toByte))
        val iter = obj.toArray.sortBy(_._1).iterator
        while (iter.hasNext) {
          val (key, value) = iter.next
          if (value != JNothing) {
            dumpString(dumper, key)
            dumper(Array(':'.toByte))
            jsonCompactDump(dumper, value) // recursive call
            while (iter.hasNext) {
              val (key2, value2) = iter.next
              if (value2 != JNothing) {
                dumper(Array(','.toByte))
                dumpString(dumper, key2)
                dumper(Array(':'.toByte))
                jsonCompactDump(dumper, value2) // recursive call
              }
            }
          }
        }
        dumper(Array('}'.toByte))
      case JArray(arr) =>
        dumper(Array('['.toByte));
        val iter = arr.iterator
        while (iter.hasNext) {
          val value = iter.next
          if (value != JNothing) {
            jsonCompactDump(dumper, value)
            while (iter.hasNext) {
              val value2 = iter.next
              if (value2 != JNothing) {
                dumper(Array(','.toByte))
                jsonCompactDump(dumper, value2) // recursive call
              }
            }
          }
        }
        dumper(Array(']'.toByte))
    }
  }

  /** returns a compact sorted json representation
    */
  def jsonCompact(value: JValue): Array[Byte] = {
    val pieces = new ListBuffer[Array[Byte]]()
    var totLen = 0
    jsonCompactDump({s: Array[Byte] =>
      pieces.append(s)
      totLen += s.length
    }, value)
    val res = new Array[Byte](totLen)
    var i0 = 0;
    for (p <- pieces) {
      p.copyToArray(res, i0, i0 + p.length)
      i0 += p.length
    }
    res
  }

  /** returns a string with a compact sorted json representation
    *
    * this isjust for convenience, try to avoid its use
    */
  def jsonCompactStr(value: JValue): String =
    new String(jsonCompact(value), StandardCharsets.UTF_8)

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
