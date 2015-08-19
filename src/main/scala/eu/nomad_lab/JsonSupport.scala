package eu.nomad_lab;

import java.io.{OutputStream, InputStream}
import java.nio.charset.StandardCharsets
import org.json4s.{DefaultFormats, Extraction, JsonInput}
import org.json4s.native.Serialization
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import java.io.{Reader, StringWriter, Writer, BufferedWriter, OutputStreamWriter, InputStreamReader}


/** methods to handle serialization of objects to json
  *
  * New serializers need to be registred here to add support for
  * new custom types.
  * having these implicit formats allows
  * extraction from and dumping to json of custom types:
  * - AST (jsonExtract, jsonDecompose)
  * - String (jsonReadString, writeString, writePrettyString)
  * - Reader/Writer (jsonReadReader, jsonWriteWriter)
  * - UTF_8 (jsonRead, jsonWrite, jsonReadInputStream, jsonWriteInputStream)
  *
  * For the
  * - parse to AST (jsonParse*)
  * - serialize it in a predictable compact way (jsonCompact*)
  * - serialize in a nicer humar readable format (jsonPretty*)
  * - merge and diff (
  *
  * Aside these methods a user probably also wants to get AST
  *
  *     import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt,
  *                        JString, JArray, JObject, JValue, JField}
  *
  * add support for custom object serialization (and json.extract[T])
  *
  *     import eu.nomad_lab.JsonSupport.{formats, read, write}
  *
  * and possibly for the DSL to write AST literals
  *
  *     import org.json4s.JsonDSL._
  *
  * First implementation, could be improved, but excluding obvious things
  * like the pretty print, that is worth doing only with real benchmarks.
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

  /** transforms the given value in a json AST (JValue)
    */
  def decompose(a: Any): JValue = Extraction.decompose(a)

  def writeNormalizedOutputStream[A <: AnyRef, O <: OutputStream](a: A, out: O): Unit = {
    JsonUtils.normalizedOutputStream(Extraction.decompose(a), out)
  }

  def writeNormalizedWriter[A <: AnyRef, W <: Writer](a: A, out: W): Unit = {
    JsonUtils.normalizedWriter(Extraction.decompose(a), out)
  }

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
  def readReader[A](in: Reader)(implicit mf: Manifest[A]): A = {
    Serialization.read[A](in)
  }

  /** Deserialize from a InputReader
   */
  def readInputStream[A](in: InputStream)(implicit mf: Manifest[A]): A = {
    readReader[A](new InputStreamReader(in, StandardCharsets.UTF_8))
  }

/*
  def writePrettyStr[A <: AnyRef](a: A): String = Serialization.writePretty(a)
  def writePrettyOutputStream[A <: AnyRef, W <: JWriter](a: A, out: W): W = Serialization.writePretty(a, out)
  def writePrettyWriter[A <: AnyRef, W <: JWriter](a: A, out: W): W = Serialization.writePretty(a, out) */
}
