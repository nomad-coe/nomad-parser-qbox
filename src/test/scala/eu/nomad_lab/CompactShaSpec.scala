package eu.nomad_lab

import org.specs2.mutable.Specification
import java.nio.charset.StandardCharsets
import com.typesafe.scalalogging.{StrictLogging}

/** Specification (fixed tests) for MetaInfo serialization
  */
class CompactShaSpec extends Specification with StrictLogging {

  sequential

  def strSha(vv: Seq[Array[Byte]]): CompactSha = {
    val sha = CompactSha()
    for (v <- vv)
      sha.update(v)
    sha
  }

  def pSha(vv: Seq[Array[Byte]]): Tuple2[CompactSha, CompactSha] = {
    val sha1 = CompactSha()
    val sha2 = CompactSha()
    for (v <- vv) {
      sha1.update(v)
      sha2.update(v)
    }
    (sha1, sha2)
  }

  "str1Sha"  >> {
    val str1Sha  = strSha(Seq("123456890\nblabla\nzz1\n".getBytes(StandardCharsets.UTF_8)))
    val str1Sha2 = strSha(Seq("123456890\n", "blabla\n", "zz1\n").map{x: String =>
      x.getBytes(StandardCharsets.UTF_8)})
    val refB64Digest = "D6bhnlk1uwpvQ-lstRwyINlkqcfJyMZBfBDvw5MGH88QjSjH87GXV66wy-am30qbqPmZ_H3ojeTxZ3kAbzcrYA"
    val refDigest = Base64.b64DecodeStr(refB64Digest)

    // the followingg requires sequential execution, as concurrent access to the digest is not supported

    "b64StrDigest correct" >> {
      str1Sha.b64StrDigest must_== refB64Digest
    }
    "repeat digest stable" >> {
      str1Sha.b64StrDigest must_== refB64Digest
    }
    "piecewise update has same result" >> {
      str1Sha.b64StrDigest must_== str1Sha2.b64StrDigest
    }
    "gid is correct" >> {
      str1Sha.gidStr("") must_== "D6bhnlk1uwpvQ-lstRwyINlkqcfJ"
    }
    "digest is correct" >> {
      str1Sha.digest must_== refDigest
    }
    "b64AsciiDigest is correct" >> {
      str1Sha.b64AsciiDigest must_== refB64Digest.getBytes(StandardCharsets.US_ASCII)
    }
    "pSha"  >> {
      val (sha1, sha2) = pSha(Seq("123456890\n", "blabla\n", "zz1\n").map{x: String =>
        x.getBytes(StandardCharsets.UTF_8)})

      "same sha" >> {
        sha1.b64StrDigest must_== sha2.b64StrDigest
      }
      "b64StrDigest correct" >> {
        sha1.b64StrDigest must_== refB64Digest
        sha2.b64StrDigest must_== refB64Digest
      }
    }
  }

  "metaInfoSha" >> {
    val mInfo = MetaInfoRecord(
      name = "sampleMeta",
      kindStr = "type_document_content",
      description = "just some test meta info"
    )

    "serialization as expected" >> {
      val expectedNormalizedStr: String = """{"description":"just some test meta info","name":"sampleMeta","superNames":[]}"""

      JsonSupport.writeNormalizedStr(mInfo) must_== expectedNormalizedStr
    }
    "sha is correct" >> {
      {
        val sha = CompactSha()
        JsonSupport.writeNormalizedOutputStream(mInfo, sha.outputStream)
        sha.b64StrDigest
      } must_== "YGLhzWIdxBAVB3rHb5MseUQYN23Oc75Fc7Amj7AYZgIJkd9kadXe1b0-JgTK_0UYoKR-yWHVlo8G0mUbnBGiqA"
    }
  }

}
