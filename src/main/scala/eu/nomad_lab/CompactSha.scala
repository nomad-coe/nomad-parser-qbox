package eu.nomad_lab;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets
import scala.collection.mutable

/** Exception thrown when a Compact sha is updated after having calculated the digest
  *
  *  most implementations support cloning to get partial digest, but as of now we avoid it
  */
class UpdateAfterDigestException(msg: String) extends Exception(msg) { }

/** Sha used internally by nomad
  *
  * SHA-512 is faster than the 224 version on 64 bit hardware, so it is used.
  * To limit the length normally a base64 encoding of the first 168 bits is used.
  * Truncation of the sha is a valid operation that does not degrade its properties
  * (more than the limited range does).
  * 168 bits can be represent exactly with base 64, and are not overly long (28 characters)
  * Its collision characteristics are thus similar to the original SHA (which has 160 bits).
  * The gid normally has a prefix that specifies how the digest was calculated.
  */
class CompactSha(val mDigest: MessageDigest) {
  object State extends Enumeration {
    val Collecting, Finished = Value
  }
  private var state = State.Collecting
  private var myDigest: Array[Byte] = Array()

  /** Resets the Sha
    */
  def reset(): Unit = {
    state = State.Collecting
    myDigest = Array()
    mDigest.reset()
  }

  /** Adds the given string (using utf8 encoding) to the sha
    */
  def updateStr(str: String): Unit = {
    update(str.getBytes(StandardCharsets.UTF_8))
  }

  /** Adds the given data to the sha
    */
  def update(binData: Array[Byte]): Unit = {
    if (state != State.Collecting)
      throw new UpdateAfterDigestException("update called after digest")
    mDigest.update(binData)
  }

  /** Calculates the sha
    *
    * Currently this disable further adding, partial digests are not supported
    */
  def digest: Array[Byte] = {
    // try to clone?
    state match {
      case State.Collecting =>
        state = State.Finished
        myDigest = mDigest.digest
      case State.Finished =>
        ()
    }
    myDigest
  }

  /** Base64 encoded digest as binary array
    */
  def b64AsciiDigest: Array[Byte] = Base64.b64EncodeAscii(digest)

  /** Base64 encoded digest as string
    */
  def b64StrDigest: String = Base64.b64EncodeStr(digest)

  /** returns a gid with the given prefix as binary array
    *
    * The prefix is expected to be ascii only.
    * The suffix added consists of the first 168 bits of the digest using base64 encoding.
    */
  def gidAscii(prefix: String): Array[Byte] =
    (prefix.getBytes(StandardCharsets.US_ASCII) ++ b64AsciiDigest).slice(0,28)

  /** returns a gid with the given prefix as string
    *
    * The prefix is expected to be ascii only.
    * The suffix added consists of the first 168 bits of the digest using base64 encoding.
    */
  def gidStr(prefix: String): String =
    new String(gidAscii(prefix), StandardCharsets.US_ASCII)
}

object CompactSha {
  def apply(): CompactSha = {
    new CompactSha(MessageDigest.getInstance("SHA-512"))
  }
}
