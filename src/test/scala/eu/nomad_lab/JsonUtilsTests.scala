package eu.nomad_lab

import org.scalacheck._
import org.scalacheck.Prop.BooleanOperators


/** Scalacheck (generated) tests for JsonUtils
  */
object JsonUtilsTests extends Properties("JsonUtils") {
  
  property("Double.toString stable") = Prop.forAll { (x: Double) =>
    val s1: String = x.toString
    val s2: String = s1.toDouble.toString
    (s1 == s2) :| "'" + s1 + "' != '" + s2 + "'"
  }

}
