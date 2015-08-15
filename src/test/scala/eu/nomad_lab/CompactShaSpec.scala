package eu.nomad_lab

import org.specs2.mutable.Specification
import java.nio.charset.StandardCharsets

/** Specification (fixed tests) for MetaInfo serialization
  */
class CompactShaSpec extends Specification {

  def strSha(vv: Seq[Array[Byte]]): CompactSha = {
    val sha = CompactSha()
    for (v <- vv)
      sha.update(v)
    sha
  }

  "str1Sha"  >> {
    val str1Sha  = strSha(Seq("123456890\nblabla\nzz1\n".getBytes(StandardCharsets.UTF_8)))
    val str1Sha2 = strSha(Seq("123456890\n", "blabla\n", "zz1\n").map{x: String =>
      x.getBytes(StandardCharsets.UTF_8)})

    str1Sha.b64StrDigest must_== "D6bhnlk1uwpvQ-lstRwyINlkqcfJyMZBfBDvw5MGH88QjSjH87GXV66wy-am30qbqPmZ_H3ojeTxZ3kAbzcrYA"
    str1Sha.b64StrDigest must_== str1Sha2.b64StrDigest
    str1Sha.gidStr("") must_== "D6bhnlk1uwpvQ-lstRwyINlkqcfJ"
  }


}
