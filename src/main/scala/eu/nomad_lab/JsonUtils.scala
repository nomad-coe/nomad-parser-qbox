package eu.nomad_lab;

import java.io.{Reader, StringWriter, Writer, InputStream, OutputStream, ByteArrayInputStream, BufferedWriter, OutputStreamWriter, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import org.json4s.native.JsonMethods.{pretty, render, parse}
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import org.json4s.{Diff, Merge, MergeDep, JsonInput}
import scala.collection.mutable.ListBuffer

/** methods to handle json (excluding custom serialization)
  *
  * In particular:
  * - parse to AST (parse*)
  * - serialize it in a predictable compact way (compact*)
  * - serialize in a nicer humar readable format (pretty*)
  * - diff two json (diff*)
  * - merge two json (merge*)
  *
  * Aside these methods a user probably also wants to get AST
  *
  *     import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt,
  *                        JString, JArray, JObject, JValue, JField}
  *
  * add support for custom object serialization
  *
  *     import eu.nomad_lab.JsonSupport.{formats, read, write}
  *
  * and possibly for the DSL to write literals
  *
  *     import org.json4s.JsonDSL._
  *
  * First implementation, could be improved, but excluding obvious things
  * like the pretty print, that is worth doing only with real benchmarks.
  */
object JsonUtils {
  /** parse the given string to the json AST
    */
  def parseStr(s: String): JValue = parse(s)

  /** parse the given UTF_8 string to the json AST
    */
  def parseUtf8(s: Array[Byte]): JValue = parse(new ByteArrayInputStream(s))

  /** parse the given string to the json AST
    */
  def parseReader(s: Reader): JValue = parse(s)

  /** parse the given string to the json AST
    */
  def parseInputStream(s: InputStream): JValue = parse(s)

  /** Adds the given amount of extra indent at the beginning of each line
    */
  class ExtraIndenter[W <: Writer](val extraIndent: Int, val dumper: W) extends Writer {
    val indent: String = "\n" + (" " * extraIndent)

    override def close(): Unit = dumper.close()

    override def flush(): Unit = dumper.flush()

    override def write(c: Int): Unit = {
      if (c == '\n'.toByte)
        dumper.write(indent)
      else
        dumper.write(c)
    }

    override def write(s: Array[Char], offset: Int, len: Int): Unit = {
      // dumper(s.replace("\n", indent))
      var i0 = offset
      var i1 = s.indexOf('\n', i0)
      var i2 = math.min(s.length, i0 + len)
      while (i1 >= 0 && i1 <= i2) {
        dumper.write(s, i0, i1 - i0)
        dumper.write(indent)
        i0 = i1 + 1
        i1 = s.indexOf('\n', i0)
      }
      dumper.write(s, i0, i2)
    }
  }

  /** Dumps the given string escaping \ and "
    */
  private def dumpString[W <: Writer](s: String, dumper: W): Unit = {
    dumper.write('"')
    var i0 = 0
    var j = i0
    while (j < s.length) {
      val c = s(j)
      if (c == '\\' || c == '"') {
        dumper.write(s, i0, j - i0)
        dumper.write('\\')
        dumper.write(c)
        i0 = j + 1
      }
      j += 1
    }
    dumper.write(s, i0, s.length - i0)
    dumper.write('"')
  }

  /** Dumps an normalized ordered json
    *
    * Object keys are alphabetically ordered.
    * This is the main reason that we cannot use the default writers.
    */
  def normalizedWriter[W <: Writer](obj: JValue, dumper: W): Unit = {
    obj match {
      case JString(s) =>
        dumpString(s, dumper)
      case JNothing =>
        ()
      case JDouble(num) =>
        dumper.write(num.toString)
      case JDecimal(num) =>
        dumper.write(num.toString)
      case JInt(num) =>
        dumper.write(num.toString)
      case JBool(value) =>
        if (value)
          dumper.write("true")
        else
          dumper.write("false")
      case JNull =>
        dumper.write("null")
      case JObject(obj) =>
        dumper.write('{')
        val iter = obj.toArray.sortBy(_._1).iterator
        while (iter.hasNext) {
          val (key, value) = iter.next
          if (value != JNothing) {
            dumpString(key, dumper)
            dumper.write(':')
            normalizedWriter(value, dumper) // recursive call
            while (iter.hasNext) {
              val (key2, value2) = iter.next
              if (value2 != JNothing) {
                dumper.write(',')
                dumpString(key2, dumper)
                dumper.write(':')
                normalizedWriter(value2, dumper) // recursive call
              }
            }
          }
        }
        dumper.write('}')
      case JArray(arr) =>
        dumper.write('[');
        val iter = arr.iterator
        while (iter.hasNext) {
          val value = iter.next
          if (value != JNothing) {
            normalizedWriter(value, dumper)
            while (iter.hasNext) {
              val value2 = iter.next
              if (value2 != JNothing) {
                dumper.write(',')
                normalizedWriter(value2, dumper) // recursive call
              }
            }
          }
        }
        dumper.write(']')
    }
  }

  /** Dumps an normalized ordered json
    *
    * Object keys are alphabetically ordered.
    * This is the main reason that we cannot use the default writers.
    */
  def normalizedOutputStream[W <: OutputStream](value: JValue, out: W): Unit = {
    val w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))
    normalizedWriter(value, w)
  }

  /** returns a normalized sorted json representation
    */
  def normalizedUtf8(value: JValue): Array[Byte] = {
    val w = new ByteArrayOutputStream()
    normalizedOutputStream(value, w)
    w.toByteArray()
  }

  /** returns a string with a normalized sorted json representation
    *
    * this is just for convenience, try to avoid its use
    */
  def normalizedStr(value: JValue): String = {
    val w = new StringWriter()
    normalizedWriter(value, w)
    w.toString()
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

  /** Dumps an indented json
    *
    * Object keys are output in generation order (not necessarily alphabetically).
    *
    * Currently inefficent, use Serialization.write?
    */
  def prettyWriter[W <: Writer](value: JValue, dumper: W): Unit =
    dumper.write(pretty(render(value)))

  /** Dumps an indented json
    *
    * Object keys are output in generation order (not necessarily alphabetically).
    *
    * Currently inefficent, use Serialization.write?
    */
  def prettyOutputStream[W <: OutputStream](value: JValue, dumper: W): Unit = {
    val out = new BufferedWriter(new OutputStreamWriter(dumper, StandardCharsets.UTF_8))
    prettyWriter(value, out)
  }

  /** Returns an UTF_8 encoded indented json
    *
    * Object keys are output in generation order (not necessarily alphabetically)
    */
  def prettyUft8(value: JValue): Array[Byte] =
    pretty(render(value)).getBytes(StandardCharsets.UTF_8)

  /** Returns a string with indented json
    *
    * Object keys are output in generation order (not necessarily alphabetically)
    */
  def prettyStr(value: JValue): String =
    pretty(render(value))

  /** Returns a json array by merging the two arguments
    */
  def mergeArray(val1: JArray, val2: JArray): JArray =
    Merge.merge(val1, val2)

  /** Returns a json object by merging the two arguments
    */
  def mergeObject(val1: JObject, val2: JObject): JObject =
    Merge.merge(val1, val2)

  /** Returns a json value by merging the two arguments
    */
  def mergeValue(val1: JValue, val2: JValue): JValue =
    Merge.merge(val1, val2)

  /** Returns the differences between two json values
    */
  def diff(val1: JValue, val2: JValue): Diff = Diff.diff(val1, val2)

}
