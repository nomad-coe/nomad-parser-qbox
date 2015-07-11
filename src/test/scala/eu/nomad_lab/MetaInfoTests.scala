package eu.nomad_lab

import org.json4s.DefaultFormats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalacheck._
import scala.util.control.NonFatal

/** Scalacheck (generated) tests for MetaInfo jsone serialization
  */
object MetaInfoJsonTests extends Properties("MetaInfoRecord") {
  implicit val formats = DefaultFormats + new eu.nomad_lab.MetaInfoRecordSerializer

  /** Generates a random MetaInfo record
    *
    * no inheritance (superNames=Seq()), kindStr = "DocumentContentType"
    */
  val genMetaInfoRecord = for {
    name <- Gen.alphaStr.suchThat(_.nonEmpty)
    description <- Gen.alphaStr.suchThat(_.nonEmpty)
    units <- Gen.frequency(
      5 -> None,
      1 -> Some("J"),
      1 -> Some("m"),
      1 -> Some("s"),
      1 -> Some("m s^-1"),
      1 -> Some("N"))
    dtypeStr <- Gen.frequency(
      10 -> None,
      1 -> Some("f"),
      1 -> Some("i"),
      1 -> Some("f64"),
      1 -> Some("f32"),
      1 -> Some("i64"),
      1 -> Some("i32"),
      1 -> Some("u64"),
      1 -> Some("u32"),
      1 -> Some("B"),
      1 -> Some("C"),
      1 -> Some("c"),
      1 -> Some("b")
    )
  } yield new MetaInfoRecord(name = name, kindStr = "DocumentContentType", description = description, units = units, dtypeStr = dtypeStr)

  /** Checks that dumping and reading back is the identity
    */
  property("dumpRead") = Prop.forAll(genMetaInfoRecord) { metaInfo =>
    val jsonStr = write(metaInfo)
    import org.scalacheck.Prop.BooleanOperators
    try {
      (metaInfo == read[MetaInfoRecord](jsonStr)) :| ("failed reading back json " + jsonStr)
    } catch {
      case NonFatal(e) =>
        false :| "failed reading back json " + jsonStr + " triggered by " + e.toString()
    }
  }

  /** Generates a sequence of MetaInfos that inherit from each other
    *
    * sequence is ordered so that dependent type come after their anchestors
    * (and no cycles)
    */
  val genMetaInfoSeq = Gen.sized { size =>
    for {
      baseMeta <- Gen.listOfN(size, genMetaInfoRecord)
      inherit <- Gen.listOfN(size, Gen.choose(1, size))
    } yield {
      val names = baseMeta.map(_.name)
      val uniqueMeta = for (
        (v,i) <- baseMeta.view.zipWithIndex if names.slice(0, i - 1).foldLeft(true){
          _ && _ != v.name
        }
      ) yield v

      val newLen = uniqueMeta.size
      val inheritSorted = inherit.filter(_ < newLen).
        groupBy((x) => x).mapValues(_.size).toArray.sortBy(_._1)
      val resMeta = for ((i, rep) <- inheritSorted) yield {
        for {
          js <- Gen.listOfN(rep, Gen.choose(0, i - 1))
          uniqueJs = js.distinct
        } yield uniqueMeta(i).copy(superNames = js.distinct.toSeq.map(uniqueMeta(_).name))
      }
    }
  }
}
