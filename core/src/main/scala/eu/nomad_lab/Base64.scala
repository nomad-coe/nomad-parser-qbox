package eu.nomad_lab;

import java.nio.charset.StandardCharsets
import scala.collection.IndexedSeqOptimized

/** Base64 encoding, by default using the URL convention and no padding
  *
  * Url convention is '-' and '_' as extra characters instead of '*' and '/'
  * no padding (with =) is done by default.
  */
object Base64 {
  final val b64UrlMapStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toArray
  final val b64UrlMap    = b64UrlMapStr.map(_.toByte)
  final val b64UrlBackMap   = {
    val res = Array.fill[Byte](256)((-1).toByte)
    for ((v, i) <- b64UrlMap.zipWithIndex) res(v & 0xff) = i.toByte
    res
  }

  def b64EncodeCore[T: Manifest](
    str : Array[Byte],
    b64Map : Array[T],
    target: Array[T] = null): Array[T] = {
    val inLen = str.length
    val outLen = (inLen * 8 + 5) / 6
    val res: Array[T] = if (target == null) new Array[T](outLen) else target
    require(res.length >= outLen, "Invalid length of target array")
    var j = 0
    for (i <- 0 until(inLen - 2, 3)) {
      val x0 = str(i) // convert to int here?
      val x1 = str(i + 1)
      val x2 = str(i + 2)
      res(j) = b64Map((x0 & 0xfc) >> 2)
      res(j + 1) = b64Map(((x0 & 0x3) << 4) | ((x1 & 0xf0) >> 4))
      res(j + 2) = b64Map(((x1 & 0xf) << 2) | ((x2 & 0xc0) >> 6))
      res(j + 3) = b64Map(x2 & 0x3f)
      j += 4
    }
    (inLen % 3) match {
      case 0 => ()
      case 1 =>
        val x0 = str(inLen - 1)
        res(j) = b64Map((x0 & 0xfc) >> 2)
        res(j + 1) = b64Map((x0 & 0x3) << 4)
        j += 2
      case 2 =>
        val x0 = str(inLen - 2)
        val x1 = str(inLen - 1)
        res(j    ) = b64Map( (x0 & 0xfc) >> 2)
        res(j + 1) = b64Map(((x0 & 0x03) << 4) | ((x1 & 0xf0) >> 4))
        res(j + 2) = b64Map( (x1 & 0x0f) << 2)
        j += 3
    }
    assert(j == outLen)
    if (res.length > outLen)
      res.slice(0, outLen)
    else
      res
  }

  def b64EncodeStr(str : Array[Byte]): String =
    new String(b64EncodeCore[Byte](str, b64UrlMap), StandardCharsets.US_ASCII)

  def b64EncodeAscii(str : Array[Byte]): Array[Byte] =
    b64EncodeCore[Byte](str, b64UrlMap)

  def b64DecodeCore[U <% Int, T <% IndexedSeq[U]](
    b64Str: T,
    b64BackMap: Array[Byte] = b64UrlBackMap,
    target : Array[Byte] = null,
    keepPartial: Boolean = false): Array[Byte] =
  {
    val inLen = b64Str.size
    val outLen = (inLen * 6 + (if (keepPartial) 7 else 0)) / 8
    val res: Array[Byte] = if (target == null) new Array[Byte](outLen) else target
    require(res.length >= outLen, "Invalid length of target array")
    var j = 0
    val it = b64Str.iterator
    for (i <- 0 until(inLen - 3, 4)) {
      val x0 = b64BackMap(it.next & 0xff)
      val x1 = b64BackMap(it.next & 0xff)
      val x2 = b64BackMap(it.next & 0xff)
      val x3 = b64BackMap(it.next & 0xff)
      assert(x0 != -1 && x1 != -1 && x2 != -1 && x3 != -1, "invalid Base64 character found")
      res(j    ) = (((x0 << 2) & 0xfc) | ((x1 >> 4) & 0x03)).toByte
      res(j + 1) = (((x1 << 4) & 0xf0) | ((x2 >> 2) & 0x0f)).toByte
      res(j + 2) = (((x2 << 6) & 0xc0) | ( x3       & 0x3f)).toByte
      j += 3
    }

    (inLen % 4) match {
      case 0 => ()
      case 1 =>
        if (keepPartial) {
          val x0 = b64BackMap(b64Str(inLen - 1) & 0xff)
          res(j    ) = ((x0 << 2) & 0xfc).toByte
          j += 1
        }
      case 2 =>
        val x0 = b64BackMap(it.next & 0xff)
        val x1 = b64BackMap(it.next & 0xff)
        res(j    ) = (((x0 << 2) & 0xfc) | ((x1 >> 4) & 0x03)).toByte
        j += 1
        if (keepPartial) {
          res(j) = ((x1 << 4) & 0xf0).toByte
          j += 1
        }
      case 3 =>
        val x0 = b64BackMap(it.next & 0xff)
        val x1 = b64BackMap(it.next & 0xff)
        val x2 = b64BackMap(it.next & 0xff)
        res(j    ) = (((x0 << 2) & 0xfc) | ((x1 >> 4) & 0x03)).toByte
        res(j + 1) = (((x1 << 4) & 0xf0) | ((x2 >> 2) & 0x0f)).toByte
        j += 2
        if (keepPartial) {
          res(j  ) = ((x2 << 6) & 0xc0).toByte
          j += 1
        }
    }
    assert(j == outLen)
    if (res.length > outLen)
      res.slice(0, outLen)
    else
      res
  }

  def b64DecodeStr(str: String, keepPartial: Boolean = false): Array[Byte] =
    b64DecodeCore[Char, String](str, b64UrlBackMap, keepPartial = keepPartial)

  def b64Decode[U <% Int, T](seq: T, keepPartial: Boolean = false)(implicit ev: T <:< IndexedSeq[U]): Array[Byte] =
    b64DecodeCore[U,T](seq, b64UrlBackMap, keepPartial = keepPartial)
}
