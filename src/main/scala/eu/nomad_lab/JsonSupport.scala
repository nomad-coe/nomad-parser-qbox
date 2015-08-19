package eu.nomad_lab;

import java.io.{OutputStream, InputStream}
import java.nio.charset.StandardCharsets
import org.json4s.{DefaultFormats, Extraction, JsonInput}
import org.json4s.native.Serialization
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import java.io.{Reader, StringWriter, Writer, BufferedWriter, OutputStreamWriter, InputStreamReader}


/** Methods to handle (de-)serialization of custom objects to (from) json
  *
  * New serializers need to be registred here to add support for
  * new custom types.
  * having these implicit formats allows
  * extraction from and dumping to json of custom types:
  * - AST (extract, decompose)
  * - String (jsonReadString, writeString, writeNormalizedString, writePrettyString)
  * - Reader/Writer (readReader, writeWriter, writeNormalizedWriter, writePrettyWriter)
  * - UTF_8 (readInputStream, writeInputStream, writeNormalizedInputStream, writePrettyInputStream)
  *
  * Aside these methods a user probably also wants to get AST
  *
  *     import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt,
  *                        JString, JArray, JObject, JValue, JField}
  *
  * merging and diff of JValues can be done with
  *
  *     import eu.nomad_lab.JsonUtils.{mergeArray, mergeObject, mergeValue, diff}
  *
  * (parsing and serialization similar to here, but limited to JValues, is also exposed there)
  *
  * and possibly for the DSL to write AST literals
  *
  *     import org.json4s.JsonDSL._
  */
object JsonSupport {
  /** list of default formats, all custom types need to be added here
    *
    * A more modular approach with split implicit formats and exposed functions
    * might be better, but let's try to keep it simple and see how far we get.
    */
  implicit val formats = DefaultFormats + new eu.nomad_lab.MetaInfoRecordSerializer

  /** initializes a type T from the given the JValue and returns it
    */
  def extract[T](json: JValue)(implicit mf: Manifest[T]): T = {
    Extraction.extract[T](json)
  }

  /** Transforms the given value in a json AST (JValue)
    */
  def decompose(a: Any): JValue = Extraction.decompose(a)

  /** writes out a normalized json to an OutputStream
    *
    * Object keys are sorted
    */
  def writeNormalizedOutputStream[A <: AnyRef, O <: OutputStream](a: A, out: O): Unit = {
    JsonUtils.normalizedOutputStream(Extraction.decompose(a), out)
  }

  /** writes out a normalized json to a Writer
    *
    * Object keys are sorted
    */
  def writeNormalizedWriter[A <: AnyRef, W <: Writer](a: A, out: W): Unit = {
    JsonUtils.normalizedWriter(Extraction.decompose(a), out)
  }

  /** returns a normalized json string
    *
    * Object keys are sorted
    */
  def writeNormalizedStr[A <: AnyRef](a: A): Unit = {
    val w = new StringWriter()
    writeNormalizedWriter(a, w)
    w.toString()
  }

  /** Serialize to OutputStream
   */
  def writeOutputStream[A <: AnyRef, O <: OutputStream](a: A, out: O): Unit = {
    Serialization.write(a,
      new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)))
  }

  /** Serialize to Writer.
   */
  def writeWriter[A <: AnyRef, W <: Writer](a: A, out: W): Unit = {
    Serialization.write(a, out)
  }

  /** Serialize to String.
   */
  def writeStr[A <: AnyRef](a: A): String = {
    Serialization.write(a)
  }

  /** Deserialize from a Reader
   */
  def readReader[A](in: Reader)(implicit mf: Manifest[A]): Unit = {
    Serialization.read[A](in)
  }

  /** Deserialize from a InputReader
   */
  def readInputStream[A](in: InputStream)(implicit mf: Manifest[A]): Unit = {
    readReader[A](new InputStreamReader(in, StandardCharsets.UTF_8))
  }

  /** Writes an indented json to a Writer
    */
  def writePrettyWriter[A <: AnyRef, W <: Writer](a: A, out: W): Unit =
    Serialization.writePretty(a, out)

  /** Writes an indented json to an OutputStream
    */
  def writePrettyOutputStream[A <: AnyRef, W <: OutputStream](a: A, out: W): Unit = {
    val w = new OutputStreamWriter(out, StandardCharsets.UTF_8)
    Serialization.writePretty(a, w)
  }

  /** Writes an indented json to a String
    */
  def writePrettyStr[A <: AnyRef](a: A): String = Serialization.writePretty(a)
}
