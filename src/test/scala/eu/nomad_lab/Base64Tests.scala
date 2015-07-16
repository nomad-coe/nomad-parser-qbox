package eu.nomad_lab

import org.scalacheck._
import org.scalacheck.Prop.BooleanOperators


/** Scalacheck (generated) tests for Base64 encoding
  */
object Base64Tests extends Properties("Base64") {

  val genB64 = Gen.listOf(Gen.oneOf(Base64.b64UrlMapStr))

  property("decodeEncode") = Prop.forAll(genB64) { b64Str =>
    val binData = Base64.b64DecodeStr(b64Str.mkString, keepPartial = true)
    val b64Str2 = Base64.b64EncodeStr(binData)
    val b64Len  = b64Str.length
    val b64Len2 = b64Str2.length

    (b64Str.mkString == b64Str2.slice(0, b64Len) && (if (b64Len2 > b64Len) b64Str2.slice(b64Len, b64Len2) == "A" else true)) :| "reencoding '" + b64Str.mkString + "' from " + binData.mkString(",") +
      " got '" + b64Str2 + "'"
  }

  property("encodeDecode") = Prop.forAll(Gen.listOf(Arbitrary.arbitrary[Byte])) { binData =>
    val b64Str = Base64.b64EncodeStr(binData.toArray)
    val binData2 = Base64.b64DecodeStr(b64Str)

    (binData == binData2.toList) :| "reencoding " + binData.mkString(",") + " from " + b64Str +
      " got " + binData2.mkString(",")
  }
}
