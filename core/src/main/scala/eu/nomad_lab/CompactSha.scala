package eu.nomad_lab;

import java.io.{InputStream, OutputStream, FilterOutputStream, FilterInputStream, BufferedWriter, Writer, Reader, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest;
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
  * The gid normally has a prefix that specifies the kind of data used to build the digest.
  *
  * Well defined prefixes
  *
  */
class CompactSha(val mDigest: MessageDigest) {
  object State extends Enumeration {
    val Collecting, Finished = Value
  }
  private var state = State.Collecting
  private var myDigest: Array[Byte] = Array()

  /** returns an OutputStream that also updates this digest
    */
  class CShaOutputStream(val out: OutputStream) extends OutputStream {
    override def write(b: Int): Unit = {
      CompactSha.this.update(b.toByte)
      out.write(b)
    }

    override def write(buf: Array[Byte]): Unit = write(buf, 0, buf.length)

    override def write(buf: Array[Byte], off: Int, len: Int): Unit = {
      CompactSha.this.update(buf, off, len)
      out.write(buf, off, len)
    }
  }

  /** returns an OutputStream that updates mDigest with UTF_8 encoded strings
    */
  class CShaNullOutputStream extends OutputStream {
    override def close(): Unit = ()

    override def flush(): Unit = ()

    override def write(b: Int): Unit = {
      CompactSha.this.update(b.toByte)
    }

    override def write(buf: Array[Byte], off: Int, len: Int): Unit = {
      CompactSha.this.update(buf, off, len)
    }
  }

  /** Wraps an InputStream in an InputStream that also updates mDigest
    */
  class CShaInputStream(in: InputStream) extends FilterInputStream(in) {
    override def markSupported(): Boolean = false

    override def read(): Int = {
      val res = super.read()
      CompactSha.this.update(res.toByte)
      res
    }

    override def read(buf: Array[Byte], off: Int, len: Int): Int = {
      val res = super.read(buf, off, len)
      if (res > 0)
        CompactSha.this.update(buf, off, res)
      res
    }

    override def skip(n: Long): Long = {
      val buf = new Array[Byte](8*1024)
      var toRead: Long = n
      var byteRead: Int = 0
      var readMore = toRead > 0
      while (readMore) {
        byteRead = in.read(buf, 0, math.min(toRead, buf.length.toLong).toInt)
        if (byteRead > 0) {
          mDigest.update(buf, 0, byteRead)
          toRead -= byteRead
          readMore = toRead > 0
        } else {
          readMore = false
        }
      }
      n - toRead
    }
  }

  /** Wraps a writer in a writer that also updates mDigest with UTF_8 encoded strings
    *
    * Warning: flushing or closing is required to ensure that all data is handled.
    * Warning: With dangling surrogate pairs at the end of file, flush and close give
    *          different results.
    */
  class CShaWriter(val writer: Writer) extends Writer {

    val utf8Converter = new BufferedWriter(new OutputStreamWriter(new CShaNullOutputStream, StandardCharsets.UTF_8))

    override def close(): Unit = {
      writer.close()
      utf8Converter.close()
    }

    override def flush(): Unit = {
      writer.flush()
      utf8Converter.flush()
    }

    override def write(c: Int): Unit = {
      writer.write(c)
      utf8Converter.write(c)
    }

    override def write(cbuf: Array[Char], off: Int, len: Int): Unit = {
      writer.write(cbuf, off, len)
      utf8Converter.write(cbuf, off, len)
    }
  }

  /** Wraps a reader in a reader that also updates mDigest using UTF_8 encoded strings
    *
    * Warning: Flushing or closing is required to ensure that all data is handled.
    * Warning: With dangling surrogate pairs at the end of file, flush and close give
    *          different results.
    */
  class CShaReader(val reader: Reader) extends Reader {

    val utf8Converter = new BufferedWriter(new OutputStreamWriter(new CShaNullOutputStream, StandardCharsets.UTF_8))

    override def close(): Unit = {
      reader.close()
      utf8Converter.close()
    }

    def flush(): Unit = {
      utf8Converter.flush()
    }

    override def read(): Int = {
      val res: Int = reader.read()
      utf8Converter.write(res)
      res
    }

    override def read(cbuf: Array[Char], off: Int, len: Int): Int = {
      val res = reader.read(cbuf, off, len)
      if (res > 0)
        utf8Converter.write(cbuf, off, res)
      res
    }
  }

  /** Resets the Sha
    */
  def reset(): Unit = {
    state = State.Collecting
    myDigest = Array()
    mDigest.reset()
  }

  /** Updates the checksum by reading all the given input stream
    */
  def readAllFromInputStream(in: InputStream): Unit = {
    val buf = new Array[Byte](8*1024)
    var byteRead = in.read(buf, 0, buf.length)
    while (byteRead > 0) {
      mDigest.update(buf, 0, byteRead)
      byteRead = in.read(buf, 0, buf.length)
    }
    assert(byteRead == -1, s"read returned an unexpected value ($byteRead)")
  }

  /** Updates the checksum with all the UTF_8 encoded strings of the given reader
    */
  def readAllFromReader(in: Reader): Unit = {
    val w = newWriter()
    val buf = new Array[Char](4*1024)
    var byteRead = in.read(buf, 0, buf.length)
    while (byteRead > 0) {
      w.write(buf, 0, byteRead)
      byteRead = in.read(buf, 0, buf.length)
    }
    w.close()
    assert(byteRead == -1, s"read returned an unexpected value ($byteRead)")
  }

  /** Returns an input stream that updates this digest when it is read from
    */
  def filterInputStream(in: InputStream): CShaInputStream =
    new CShaInputStream(in)

  /** Returns an output stream that updates this digest when it is written to
    */
  def filterOutputStream(out: OutputStream): CShaOutputStream =
    new CShaOutputStream(out)

  /** Returns a Writer that updates this digest with UTF_8 strings when it is written to
    *
    * Warning: Flushing or closing is required to ensure that all data is handled.
    * Warning: With dangling surrogate pairs at the end of file, flush and close give
    *          different results.
    */
  def filterWriter(out: Writer): CShaWriter =
    new CShaWriter(out)

  /** Returns a Reader that updates this digest with UTF_8 strings when it is read from
    *
    * Warning: Flushing or closing is required to ensure that all data is handled.
    * Warning: With dangling surrogate pairs at the end of file, flush and close give
    *          different results.
    */
  def filterReader(in: Reader): CShaReader =
    new CShaReader(in)

  /** Returns a writer that updates this digest with UTF_8 strings
    *
    * Does not forward the data anywhere else.
    *
    * Warning: Flushing or closing is required to ensure that all data is handled.
    * Warning: With dangling surrogate pairs at the end of file, flush and close give
    *          different results.
    */
  def newWriter(): Writer =
    new BufferedWriter(new OutputStreamWriter(new CShaNullOutputStream, StandardCharsets.UTF_8))

  /** Returns an OutputStream that updates this digest
    *
    * Does not forward the data anywhere else.
    */
  def outputStream(): OutputStream =
    new CShaNullOutputStream

  /** Adds the given string (using utf8 encoding) to the sha
    */
  def updateStr(str: String): Unit = {
    update(str.getBytes(StandardCharsets.UTF_8))
  }

  /** Adds the given data to the sha
    */
  def update(b: Byte): Unit = {
    if (state != State.Collecting)
      throw new UpdateAfterDigestException("update called after digest")
    mDigest.update(b)
  }

  /** Adds the given data to the sha
    */
  def update(binData: Array[Byte]): Unit = {
    update(binData, 0, binData.length)
  }

  /** Adds the given data to the sha
    */
  def update(binData: Array[Byte], off: Int, len: Int): Unit = {
    if (state != State.Collecting)
      throw new UpdateAfterDigestException("update called after digest")
    mDigest.update(binData, off, len)
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
    (prefix.getBytes(StandardCharsets.ISO_8859_1) ++ b64AsciiDigest).slice(0,28)

  /** returns a gid with the given prefix as string
    *
    * The prefix is expected to be ascii only.
    * The suffix added consists of the first 168 bits of the digest using base64 encoding.
    */
  def gidStr(prefix: String): String =
    new String(gidAscii(prefix), StandardCharsets.ISO_8859_1)
}

object CompactSha {
  def apply(): CompactSha = {
    new CompactSha(MessageDigest.getInstance("SHA-512"))
  }
}
