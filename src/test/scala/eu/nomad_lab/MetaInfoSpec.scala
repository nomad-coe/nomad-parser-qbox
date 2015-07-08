package eu.nomad_lab

import org.specs2._
import org.json4s.DefaultFormats
//import eu.nomad_lab.MetaInfoRecord
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import org.json4s._
import org.json4s.native.JsonMethods._

import org.scalacheck._
import Arbitrary.arbitrary
import scala.util.control.NonFatal

class MetaInfoJsonSpec extends mutable.Specification {

// meta types are "MetaType", "UnknownType", "UnknownMetaType", "MetaType", "DocumentContentType", "ConnectionType", "AbstractDocumentContentType", "SectionType"

implicit val formats = DefaultFormats + new eu.nomad_lab.MetaInfoRecordSerializer

/*
    val genNode = for {
      v <- arbitrary[Int]
      left <- genTree
      right <- genTree
    } yield Node(left, right, v)

    def genTree: Gen[Tree] = oneOf(genLeaf, genNode)

def matrix[T](g: Gen[T]): Gen[Seq[Seq[T]]] = Gen.sized { size =>
      val side = scala.math.sqrt(size).asInstanceOf[Int]
      Gen.listOfN(side, Gen.listOfN(side, g))
    }*/

  "jsonExtract basic" >> {
    val jVal = parse("""
    {
        "name": "TestProperty1",
        "description": "a meta info property to test serialization to json",
        "superNames": []
    }""")
    val mRecord = jVal.extract[MetaInfoRecord]
    mRecord.name must_== "TestProperty1"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must beEmpty
    mRecord.kindStr must_== "DocumentContentType"
    mRecord.units must beNone
    mRecord.repeats must beNone
    mRecord.shape must beNone
    mRecord.otherKeys must beEmpty
  }

  "jsonRead basic" >> {
    val mRecord = read[MetaInfoRecord]("""
    {
        "name": "TestProperty1",
        "description": "a meta info property to test serialization to json",
        "superNames": []
    }""")
    mRecord.name must_== "TestProperty1"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must beEmpty
    mRecord.kindStr must_== "DocumentContentType"
    mRecord.units must beNone
    mRecord.repeats must beNone
    mRecord.shape must beNone
    mRecord.dtypeStr must beNone
    mRecord.otherKeys must beEmpty
  }

  "jsonRead extended" >> {
    val mRecord = read[MetaInfoRecord]("""
    {
        "name": "TestProperty2",
        "kindStr": "DocumentContentType"
        "description": "a meta info property to test serialization to json",
        "superNames": [],
        "units": "pippo",
        "dtypeStr": "f64",
        "repeats": false,
        "shape": []
    }""")
    mRecord.name must_== "TestProperty2"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must beEmpty
    mRecord.kindStr must_== "DocumentContentType"
    mRecord.units must_== Some("pippo")
    mRecord.repeats must_== Some(false)
    mRecord.shape must_== Some(Seq())
    mRecord.otherKeys must beEmpty
  }

  "jsonRead null" >> {
    val mRecord = read[MetaInfoRecord]("""
    {
        "name": "TestProperty3",
        "kindStr": "DocumentContentType"
        "description": "a meta info property to test serialization to json",
        "superNames": ["TestProperty"],
        "units": null,
        "dtypeStr": null,
        "repeats": null,
        "shape": null
    }""")
    mRecord.name must_== "TestProperty3"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must_== Seq("TestProperty")
    mRecord.kindStr must_== "DocumentContentType"
    mRecord.units must beNone
    mRecord.repeats must beNone
    mRecord.shape must beNone
    mRecord.otherKeys must beEmpty
  }

  "jsonRead extra" >> {
    val mRecord = read[MetaInfoRecord]("""
    {
        "name": "TestProperty4",
        "kindStr": "DocumentContentType"
        "description": "a meta info property to test serialization to json",
        "superNames": ["TestProperty"],
        "extra": "nr1",
        "tt": 4
    }""")
    mRecord.name must_== "TestProperty4"
    mRecord.description must_== "a meta info property to test serialization to json"
    mRecord.superNames must_== Seq("TestProperty")
    mRecord.kindStr must_== "DocumentContentType"
    mRecord.units must beNone
    mRecord.repeats must beNone
    mRecord.shape must beNone
    mRecord.otherKeys must_==  JField("tt", JInt(4)) :: JField("extra", JString("nr1")) :: Nil
  }

}

object MetaInfoJsonTests extends Properties("MetaInfoRecord") {
  implicit val formats = DefaultFormats + new eu.nomad_lab.MetaInfoRecordSerializer

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
