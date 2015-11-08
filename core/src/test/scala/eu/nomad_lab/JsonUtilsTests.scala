package eu.nomad_lab

import org.scalacheck.{Arbitrary, Gen, Prop, Properties, Shrink}
import org.scalacheck.Prop.BooleanOperators
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import org.json4s.native.JsonMethods.parse
import scala.collection.mutable.ArrayOps
import scala.util.control.NonFatal
import com.typesafe.scalalogging.StrictLogging

/** Scalacheck (generated) tests for JsonUtils
  */
object JsonUtilsTests extends Properties("JsonUtils") with StrictLogging {

  val genJInt = for (i <- Arbitrary.arbitrary[Long]) yield JInt(i)
  val genJLong = for (i <- Arbitrary.arbitrary[Long]) yield JInt(i)
  val genJDouble = for (d <- Arbitrary.arbitrary[Double]) yield JDouble(d)
  val genJDecimal = for (d <- Arbitrary.arbitrary[Double]) yield JDecimal(BigDecimal(d))
  val genJBoolean = for (b <- Arbitrary.arbitrary[Boolean]) yield JBool(b)
  val genJString = for (s <- Gen.alphaStr) yield JString(s)
  val genArbJString = for (s <- Arbitrary.arbitrary[String]) yield JString(s)

  case class JGenParams(
    useDecimal: Boolean = false,
    useNothing: Boolean = true,
    useLong: Boolean = false,
    alphaStr: Boolean = true
  ) {
    override def toString(): String = {
      "double=" + (if (useDecimal) "decimal" else "double") +
        (if (useNothing) " with JNothing" else "") +
        (if (useLong) " int=long" else " int=BigInt")
    }
  }

  /** generates a random atomic json value (number, string, boolean, string, or null)
    */
  def genJAtomicValue(jParams: JGenParams) = Gen.frequency(
    Seq(
      (if (jParams.useLong) 0 else 2) -> (genJInt: Gen[JValue]),
      (if (jParams.useDecimal) 0 else 2) -> genJDouble,
      (if (jParams.useDecimal) 2 else 0) -> genJDecimal,
      2 -> genJBoolean,
      2 -> (if (jParams.alphaStr) genJString else genArbJString),
      (if (jParams.useNothing) 1 else 0) -> (JNothing: Gen[JValue]),
      1 -> (JNull: Gen[JValue]),
      (if (jParams.useLong) 2 else 0) -> genJLong
    ).filter(_._1 > 0): _*)

  /** helper method, generates a random array of json values
    */
  def genJValues(jParams: JGenParams)(param: Gen.Parameters): List[JValue] = {
    val rng = param.rng
    val newCompositeSize = rng.nextInt(param.size + 1)
    var sizeLeft: Int = param.size
    val compositeValues = new Array[JValue](newCompositeSize)
    for (i <- 0 until newCompositeSize) {
      val value: Option[JValue] = genJValue(jParams)(param.withSize(sizeLeft))
      compositeValues(i) = value match {
        case None =>
            JNothing
        case Some(v) =>
          sizeLeft = math.max(0, sizeLeft - JsonUtils.jsonComplexity(v))
          v
      }
    }
    rng.shuffle(compositeValues.toList)
  }

  /** generates a random json array
    */
  def genJArray(jParams: JGenParams): Gen[JArray] = Gen.parameterized[JArray] { param: Gen.Parameters =>
    val compositeValues = genJValues(jParams)(param)
    JArray(compositeValues.toList)
  }

  /** Generates a random json object
    */
  def genJObject(jParams: JGenParams): Gen[JObject] = Gen.parameterized[JObject] { param: Gen.Parameters =>
    val rng = param.rng
    val values = genJValues(jParams)(param)
    val keys = for (i <- 0.to(values.size + 2)) yield
      new String(rng.alphanumeric.take(rng.nextInt(25)).toArray)
    val uniqueKeys = keys.distinct
    var obj: List[JField] = Nil
    for ((k,v) <- keys.zip(values))
      obj = JField(k,v) +: obj
    JObject(obj)
  }



  /** Generates a random json value
    */
  def genJValue(jParams: JGenParams): Gen[JValue] = Gen.sized { size: Int =>
    if (size < 1) {
      genJAtomicValue(jParams)
    } else {
      Gen.frequency(
        1 -> genJAtomicValue(jParams),
        1 -> genJArray(jParams),
        1 -> genJObject(jParams)
      )
    }
  }

  /** Generates a random json root object: an array or object
    */
  def genJComposite(jParams: JGenParams): Gen[JValue] = Gen.frequency(
    1 -> genJArray(jParams),
    1 -> genJObject(jParams)
  )

  implicit lazy val arbJArray: Arbitrary[JArray] = Arbitrary(genJArray(JGenParams(false,false)))
  implicit lazy val arbJObject: Arbitrary[JObject] = Arbitrary(genJObject(JGenParams(false,false)))
  implicit lazy val arbJValue: Arbitrary[JValue] = Arbitrary(genJComposite(JGenParams(false,false)))

  class RecursiveJArrayShrink(
    val obj: List[JValue],
    var i: Int = 0,
    var shrinkedIt: Iterator[JValue] = null
  ) extends Iterator[JArray] {

    if (shrinkedIt == null && i < obj.size)
      shrinkedIt = shrinkJValue.shrink(obj(i)).iterator

    private def getMore(): Unit = {
      if (shrinkedIt != null) {
        while (! shrinkedIt.hasNext && i + 1 < obj.size) {
          i += 1
          shrinkedIt = shrinkJValue.shrink(obj(i)).iterator
        }
      }
    }

    override def hasNext: Boolean = {
      if (shrinkedIt != null && shrinkedIt.hasNext) {
        true
      } else {
        getMore()
        shrinkedIt != null && shrinkedIt.hasNext
      }
    }

    override def next(): JArray = {
      getMore()
      JArray(obj.updated(i, shrinkedIt.next()))
    }
  }

  implicit val shrinkJArray: Shrink[JArray] = Shrink{
    case JArray(obj) =>
      {
        obj.size match {
          case 0 => Stream()
          case 1 => Stream(JArray(List()))
          case 2 => Stream(JArray(obj.slice(0,1)), JArray(obj.slice(1,2)))
          case 3 => Stream(
            JArray(obj.slice(0,2)),
            JArray(obj.slice(1,3)),
            JArray(List(obj(0), obj(2))))
          case _ => Stream(
            JArray(obj.slice(0, obj.size / 2)),
            JArray(obj.slice(obj.size / 2, obj.size)),
            JArray(obj.slice(0, obj.size / 4) ++ obj.slice(3 * obj.size / 4, obj.size)))
        }
      }.append(
        new RecursiveJArrayShrink(obj)
      ).take(6) // limit recursion
  }

  class RecursiveJObjectShrink(
    val obj: List[JField],
    var i: Int = 0,
    var shrinkedIt: Iterator[JValue] = null
  ) extends Iterator[JObject] {

    if (shrinkedIt == null && i < obj.size)
      shrinkedIt = shrinkJValue.shrink(obj(i)._2).iterator

    private def getMore(): Unit = {
      if (shrinkedIt != null) {
        while (! shrinkedIt.hasNext && i + 1 < obj.size) {
          i += 1
          shrinkedIt = shrinkJValue.shrink(obj(i)._2).iterator
        }
      }
    }

    override def hasNext: Boolean = {
      if (shrinkedIt != null && shrinkedIt.hasNext)
        true
      else {
        getMore()
        shrinkedIt != null && shrinkedIt.hasNext
      }
    }

    override def next(): JObject = {
      getMore()
      val JField(k, _) = obj(i)
      JObject(obj.updated(i, JField(k, shrinkedIt.next())))
    }
  }

  val shrinkJObject: Shrink[JObject] = Shrink{
    case JObject(obj) =>
      {
        obj.size match {
          case 0 => Stream()
          case 1 => Stream(JObject(List()))
          case 2 => Stream(JObject(obj.slice(0,1)), JObject(obj.slice(1,2)))
          case 3 => Stream(
            JObject(obj.slice(0,2)),
            JObject(obj.slice(1,3)),
            JObject(List(obj(0), obj(2))))
          case _ => Stream(
            JObject(obj.slice(0, obj.size / 2)),
            JObject(obj.slice(obj.size / 2, obj.size)),
            JObject(obj.slice(0, obj.size / 4) ++ obj.slice(3 * obj.size / 4, obj.size)))
        }
      }.append(
        new RecursiveJObjectShrink(obj)
      ).take(6) // limit recursion
  }

  implicit val shrinkJValue: Shrink[JValue] = Shrink { jVal: JValue =>
    jVal match {
      case JObject(obj) =>
        if (obj.size == 1)
          obj(0)._2 #:: (shrinkJObject.shrink(JObject(obj)): Stream[JValue])
        else
          shrinkJObject.shrink(JObject(obj))
      case JArray(arr) =>
        if (arr.size == 1)
          arr(0) #:: (shrinkJArray.shrink(JArray(arr)): Stream[JValue])
        else
          shrinkJArray.shrink(JArray(arr))
      case _ =>
        Stream()
    }
  }

  property("Double.toString stable") = Prop.forAll { (x: Double) =>
    val s1: String = x.toString
    val s2: String = s1.toDouble.toString
    (s1 == s2) :| "'" + s1 + "' != '" + s2 + "'"
  }

  property("Double.toString no loss") = Prop.forAll { (x: Double) =>
    val x1: Double = x.toString.toDouble
    (x == x1) :| "'" + x1 + "' string conversion was lossy"
  }

  /*
  // this property should fail and be shrinked, but somehow no shrinking takes place
  // shrink is called, but no shrinked values are tested
  property("failureShrink") = Prop.forAll{ (value: JValue) =>
      val s1 = JsonUtils.normalizedStr(value)
      try {
        val value2 = parse(s1, useBigDecimalForDouble = false)
        val s2 = JsonUtils.normalizedStr(value2)
        val v1 = (s1 == s2)
        val v2 = (value2 match { case JArray(arr) => (arr.size < 1 || (arr(0) match {case JInt(_) => false; case _ => true})); case _ => true})
        if (!(v1 && v2))
          logger.warn(s"failure ${v1} ${v2} for json of complexity ${JsonUtils.jsonComplexity(value)}: '$s1'")
        else
          logger.info(s"success ${v1} ${v2} for json of complexity ${JsonUtils.jsonComplexity(value)}: '$s1'")
        (s1 == s2 && (value2 match { case JArray(arr) => (arr.size < 1 || (arr(0) match {case JInt(_) => false; case _ => true})); case _ => true})) :| "'" + s1 + "' reserialization gives '" + s2 + "'"
      } catch { case NonFatal(e) =>
          logger.info(s"parsing failure for json of complexity ${JsonUtils.jsonComplexity(value)}: '$s1'")
          false :| "parsing failure of string '" + s1 + "': " + e.toString
      }
    }
  */

  for (useDecimal <- Seq(false, true)) {
    val jParams = JGenParams(useDecimal, true)

    property("stable serialization" + jParams.toString) = Prop.forAll(genJComposite(jParams)){ (value: JValue) =>
      val s1 = JsonUtils.normalizedStr(value)
      try {
        val value2 = parse(s1, useBigDecimalForDouble = jParams.useDecimal)
        val s2 = JsonUtils.normalizedStr(value2)
        (s1 == s2) :| "'" + s1 + "' reserialization gives '" + s2 + "'"
      } catch { case NonFatal(e) =>
          false :| "parsing failure of string '" + s1 + "': " + e.toString
      }
    }
  }

   /*
   // comparison of some json might loop forever (bug in json comparison?)
   for (useDecimal <- Seq(false)) {
    val jParams = JGenParams(useDecimal, false, false)
    property("lossless serialization" + jParams.toString) = Prop.forAll(genJComposite(jParams)){ (value: JValue) =>
      val s1 = JsonUtils.normalizedStr(value)
      try {
        val value2 = parse(s1, useBigDecimalForDouble = jParams.useDecimal)
        (value == value2) :| "'" + s1 + "' did not capture all info (lossy serialization)"
      } catch { case NonFatal(e) =>
          false :| "parsing of '" + s1 + "' failed: " + e.toString
      }
    }
  }
  */
}
