package eu.nomad_lab

import org.scalacheck.{Properties, Prop, Gen, Arbitrary}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Prop.BooleanOperators
import java.io.{InputStream, OutputStream, SequenceInputStream, ByteArrayInputStream, Reader, Writer, StringWriter, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import scala.collection.JavaConversions.asJavaEnumeration
import scala.collection.mutable.WrappedArray

/** Scalacheck (generated) tests for CompactSha
  */
object CompactShaTests extends Properties("CompactSha") {
  /** Helper function to join arrays
    */
  def joinArray[A: Manifest](data: Seq[WrappedArray[A]]): Array[A] = {
    val newSize = data.foldLeft(0){_ + _.length}
    val res = new Array[A](newSize)
    var i0 = 0

    for (d <- data) {
      d.copyToArray(res, i0)
      i0 += d.length
    }

    res
  }

  /** Super class of the classes used to test the data input in the CompactSha
    *
    * Every subclass uses the similarly named method to input to give data
    * to the CompactSha
    */
  sealed abstract class DataIn {
    /** This method should return all the data that is feed to the CompactSha
      */
    def allData: Array[Byte]

    /** This method should add the data to the given CompactSha
      */
    def addToSha(sha: CompactSha): Unit
  }

  case class ReadAllFromInputStream(val data: Seq[WrappedArray[Byte]]) extends DataIn {
    def allData: Array[Byte] = joinArray(data)

    def addToSha(sha: CompactSha): Unit = {
      val in = new SequenceInputStream(data.map{ x: WrappedArray[Byte] =>
        new ByteArrayInputStream(x.array)
      }.toIterator
      )
      sha.readAllFromInputStream(in)
    }
  }

  val genReadAllFromInputStream = for {
    d <- arbitrary[Seq[WrappedArray[Byte]]]
  } yield new ReadAllFromInputStream(d)

  /** Simulates a reader that reads the data in the given blocks
    */
  class FakeReader(var data: Seq[WrappedArray[Char]]) extends Reader {
    data = data.dropWhile(_.isEmpty)

    override def close(): Unit = {
      data = Seq()
    }

    override def read(buf: Array[Char], off: Int, len: Int): Int = {
      if (data.isEmpty) {
        -1
      } else if (data.head.length > len) {
        data.head.copyToArray(buf, off, len)
        data = data.head.drop(len) +: data.drop(1)
        len
      } else {
        val charCopied = data.head.length
        data.head.copyToArray(buf, off)
        data = data.drop(1).dropWhile(_.isEmpty)
        charCopied
      }
    }
  }

  case class ReadAllFromReader(val data: Seq[WrappedArray[Char]]) extends DataIn {
    def allData: Array[Byte] = (new String(joinArray(data))).getBytes(StandardCharsets.UTF_8)

    def addToSha(sha: CompactSha): Unit = {
      val in = new FakeReader(data)
      sha.readAllFromReader(in)
    }
  }

  val genReadAllFromReader = for {
    d <- arbitrary[Seq[WrappedArray[Char]]] // use a String? this can have invalid character sequences...
  } yield new ReadAllFromReader(d)

  case class FilterInputStream(val data: Seq[WrappedArray[Byte]]) extends DataIn {
    def allData: Array[Byte] = joinArray(data)

    def addToSha(sha: CompactSha): Unit = {
      val in1 = new SequenceInputStream(data.map{ x : WrappedArray[Byte] =>
        new ByteArrayInputStream(x.array)
      }.toIterator)
      val in2 = sha.filterInputStream(in1)
      val out = new ByteArrayOutputStream()
      val buf = new Array[Byte](8*1024)
      var readMore = true
      while (readMore) {
        val didRead = in2.read(buf) // does not test single char read
        if (didRead > 0)
          out.write(buf, 0, didRead)
        else
          readMore = false
      }
      in2.close()
      val dataTrasmitted = out.toByteArray

      if (!(dataTrasmitted sameElements allData))
        throw new Exception(s"CompactSha.filterInputStream did not pass on the data correctly:'${dataTrasmitted.mkString(" ")}' vs '${allData.mkString(" ")}'")
    }
  }

  val genFilterInputStream = for {
    d <- arbitrary[Seq[WrappedArray[Byte]]]
  } yield new FilterInputStream(d)

  case class FilterOutputStream(
    val data: Seq[WrappedArray[Byte]],
    val useSingleCharWrite: Boolean
  ) extends DataIn {
    def allData: Array[Byte] = joinArray(data)

    def addToSha(sha: CompactSha): Unit = {
      val out1 = new ByteArrayOutputStream()
      val out2 = sha.filterOutputStream(out1)
      for (d <- data) {
        if (useSingleCharWrite && d.length == 1)
          out2.write(d(0))
        else
          out2.write(d.array)
      }
      out2.close()

      val dataTrasmitted = out1.toByteArray

      if (!(dataTrasmitted sameElements allData))
        throw new Exception(s"CompactSha.filterOutputStream did not pass on the data correctly:'${dataTrasmitted.mkString(" ")}' vs '${allData.mkString(" ")}'")
    }
  }

  val genFilterOutputStream = for {
    d <- arbitrary[Seq[WrappedArray[Byte]]]
    sChar <- arbitrary[Boolean]
  } yield new FilterOutputStream(d, sChar)

  case class FilterWriter(val data: Seq[WrappedArray[Char]], val useSingleCharWrite: Boolean) extends DataIn {
    def allData: Array[Byte] = (new String(joinArray(data))).getBytes(StandardCharsets.UTF_8)

    def addToSha(sha: CompactSha): Unit = {
      val out1 = new StringWriter
      val out2 = sha.filterWriter(out1)
      for (d <- data) {
        if (useSingleCharWrite && d.length == 1)
          out2.write(d(0))
        else
          out2.write(d.array)
      }
      out2.close()

      val dataTrasmitted = out1.toString
      if (!(dataTrasmitted sameElements joinArray(data)))
        throw new Exception(s"CompactSha.filterWriter did not pass on the data correctly:'$dataTrasmitted' vs '${joinArray(data).mkString}'")
    }
  }

  val genFilterWriter = for {
    d <- arbitrary[Seq[WrappedArray[Char]]]
    sChar <- arbitrary[Boolean]
  } yield new FilterWriter(d, sChar)

  case class FilterReader(val data: Seq[WrappedArray[Char]]) extends DataIn {
    def allData: Array[Byte] = (new String(joinArray(data))).getBytes(StandardCharsets.UTF_8)

    def addToSha(sha: CompactSha): Unit = {
      val in1 = new FakeReader(data)
      val in2 = sha.filterReader(in1)
      val out = new StringWriter
      val buf = new Array[Char](4*1024)
      var readMore = true
      while (readMore) {
        val didRead = in2.read(buf) // does not test single char read
        if (didRead > 0)
          out.write(buf, 0, didRead)
        else
          readMore = false
      }
      in2.close()
      val dataTrasmitted = out.toString

      if (!(dataTrasmitted sameElements joinArray(data)))
        throw new Exception(s"CompactSha.filterReader did not pass on the data correctly:'$dataTrasmitted' vs '${joinArray(data).mkString}'")
    }
  }

  val genFilterReader = for {
    d <- arbitrary[Seq[WrappedArray[Char]]]
  } yield new FilterReader(d)

  case class NewWriter(val data: Seq[WrappedArray[Char]], val useSingleCharWrite: Boolean) extends DataIn {
    def allData: Array[Byte] = (new String(joinArray(data))).getBytes(StandardCharsets.UTF_8)

    def addToSha(sha: CompactSha): Unit = {
      val out = sha.newWriter()
      for (d <- data) {
        if (useSingleCharWrite && d.length == 1)
          out.write(d(0))
        else
          out.write(d.array)
      }
      out.close()
    }
  }

  val genNewWriter = for {
    d <- arbitrary[Seq[WrappedArray[Char]]]
    sChar <- arbitrary[Boolean]
  } yield new NewWriter(d, sChar)

  case class OutputStreamAdd(val data: Seq[WrappedArray[Byte]], val useSingleCharWrite: Boolean) extends DataIn {
    def allData: Array[Byte] = joinArray(data)

    def addToSha(sha: CompactSha): Unit = {
      val out = sha.outputStream
      for (d <- data) {
        if (useSingleCharWrite && d.length == 1)
          out.write(d(0))
        else
          out.write(d.array)
      }
      // do not close, should also work
    }
  }

  val genOutputStreamAdd = for {
    d <- arbitrary[Seq[WrappedArray[Byte]]]
    sChar <- arbitrary[Boolean]
  } yield new OutputStreamAdd(d, sChar)

  case class UpdateStr(val data: String) extends DataIn {
    def allData: Array[Byte] = data.getBytes(StandardCharsets.UTF_8)

    def addToSha(sha: CompactSha): Unit = sha.updateStr(data)
  }

  val genUpdateStr = for {
    d <- arbitrary[String]
  } yield new UpdateStr(d)

  case class UpdateByte(val data: Byte) extends DataIn {
    def allData: Array[Byte] = Array(data)

    def addToSha(sha: CompactSha): Unit = sha.update(data)
  }

  val genUpdateByte = for {
    b <- arbitrary[Byte]
  } yield new UpdateByte(b)

  case class UpdateArray(val data: WrappedArray[Byte]) extends DataIn {
    def allData: Array[Byte] = data.array

    def addToSha(sha: CompactSha): Unit = sha.update(data.array)
  }

  val genUpdateArray = for {
    d <- arbitrary[WrappedArray[Byte]]
  } yield new UpdateArray(d)

  case class UpdateArray2(
    val data: WrappedArray[Byte],
    val preData: WrappedArray[Byte],
    val postData: WrappedArray[Byte]
  ) extends DataIn {
    def allData: Array[Byte] = data.array

    def addToSha(sha: CompactSha): Unit = {
      sha.update((preData ++ data ++ postData).array, preData.length, data.length)
    }
  }

  val genUpdateArray2 = for {
    d <- arbitrary[WrappedArray[Byte]]
    pre <- arbitrary[WrappedArray[Byte]]
    post <- arbitrary[WrappedArray[Byte]]
  } yield new UpdateArray2(d, pre, post)

  val genSomeUpdate = Gen.oneOf(
    genReadAllFromInputStream,
    genReadAllFromReader,
    genFilterInputStream,
    genFilterOutputStream,
    genFilterWriter,
    genFilterReader,
    genNewWriter,
    genOutputStreamAdd,
    genUpdateStr,
    genUpdateByte,
    genUpdateArray,
    genUpdateArray2)

  property("single input method stable") = Prop.forAll(genSomeUpdate) { dataIn =>
    val sha = CompactSha()
    val shaRef = CompactSha()
    dataIn.addToSha(sha)
    shaRef.update(dataIn.allData)

    val shaDigest = sha.b64StrDigest
    val shaRefDigest = shaRef.b64StrDigest
    (shaDigest == shaRefDigest) :| s"input method gives unexpected result ($shaDigest vs $shaRefDigest)"
  }

  property("multiple input methods stable") = Prop.forAll(Gen.listOf(genSomeUpdate)) { dataIns =>
    val sha = CompactSha()
    val shaRef = CompactSha()
    for (d <- dataIns)
      d.addToSha(sha)
    val collectedData = joinArray(dataIns.map{ x: DataIn =>
      WrappedArray.make[Byte](x.allData)
    }.toSeq)
    shaRef.update(collectedData)

    val shaDigest = sha.b64StrDigest
    val shaRefDigest = shaRef.b64StrDigest
    (shaDigest == shaRefDigest) :| s"sequence of methods gives unexpected result ($shaDigest vs $shaRefDigest)"
  }

}
