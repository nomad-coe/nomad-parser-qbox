package eu.nomad_lab.parsers;
import org.specs2.mutable.Specification
import ucar.ma2

/** Specification (fixed tests) for MetaInfo serialization
  */
class JsonWriterBackendSpec extends Specification {
  val a1 = new ma2.ArrayDouble.D2(3,2)

  "a2f" >> {
    val writer = new java.io.StringWriter
    JsonWriterBackend.writeNArrayWithDtypeStr(a1, "f64", writer)
    val out = writer.toString()
    out must_== """[[0.0, 0.0],
 [0.0, 0.0],
 [0.0, 0.0]]"""
  }
}
