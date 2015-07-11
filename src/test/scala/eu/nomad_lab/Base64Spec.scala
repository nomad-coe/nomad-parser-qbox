package eu.nomad_lab

import org.specs2._
import java.nio.charset.StandardCharsets

/** Specification (fixed tests) for MetaInfo serialization
  */
class Base64Spec extends mutable.Specification {
  val b64Samples = Seq(
    Array[Byte](-1) -> "_w",
    Array[Byte](-8, -1) -> "-P8",
    Array[Byte](-8, -1, 97) -> "-P9h",
    "Man is distinguished, not only by his reason, but by this singular passion from other animals, which is a lust of the mind, that by a perseverance of delight in the continued and indefatigable generation of knowledge, exceeds the short vehemence of any carnal pleasure.".getBytes(StandardCharsets.US_ASCII)
      ->
      "TWFuIGlzIGRpc3Rpbmd1aXNoZWQsIG5vdCBvbmx5IGJ5IGhpcyByZWFzb24sIGJ1dCBieSB0aGlzIHNpbmd1bGFyIHBhc3Npb24gZnJvbSBvdGhlciBhbmltYWxzLCB3aGljaCBpcyBhIGx1c3Qgb2YgdGhlIG1pbmQsIHRoYXQgYnkgYSBwZXJzZXZlcmFuY2Ugb2YgZGVsaWdodCBpbiB0aGUgY29udGludWVkIGFuZCBpbmRlZmF0aWdhYmxlIGdlbmVyYXRpb24gb2Yga25vd2xlZGdlLCBleGNlZWRzIHRoZSBzaG9ydCB2ZWhlbWVuY2Ugb2YgYW55IGNhcm5hbCBwbGVhc3VyZS4"
  )

  "b64Encode" >> {
    examplesBlock {
      for (((bData, b64),i) <- b64Samples.zipWithIndex) {
        "encode " + i in {
          Base64.b64EncodeStr(bData) must_== b64
        }
      }
    }
  }

  "b64Decode" >> {
    examplesBlock {
      for (((bData, b64),i) <- b64Samples.zipWithIndex) {
        "decode " + i in {
          Base64.b64DecodeStr(b64) must_== bData
        }
      }
    }
  }

}
