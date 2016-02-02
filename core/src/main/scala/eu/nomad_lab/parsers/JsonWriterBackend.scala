package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import ucar.ma2.{IndexIterator => NIndexIterator}
import ucar.ma2.DataType
import ucar.ma2.ArrayString
import scala.collection.mutable
import scala.collection.breakOut
import scala.collection.mutable.ListBuffer
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import java.io.Writer

object JsonWriterBackend {
  /** Utility function to write a NArray to a writer
    */
  def writeNArray(array: NArray, writeNextEl: NIndexIterator => Unit, writer: Writer): Unit = {
    val shape = array.getShape()
    val it = array.getIndexIterator()
    val idx = Array.fill[Int](shape.size)(0)
    writer.write("[" * shape.size)
    if (!shape.find( _ <= 0 ).isEmpty) {
      writer.write("]" * shape.size)
    } else {
      var comma: Boolean = false
      while (it.hasNext()) {
        var ii: Int = shape.size - 1
        // inner write
        for (i <- 0.until(shape(ii))) {
          if (comma)
            writer.write(", ")
          else
            comma = true
          writeNextEl(it)
        }
        idx(ii) = shape(ii)
        var toClose: Int = 0
        while (ii > 0 && idx(ii) == shape(ii)) {
          idx(ii) = 0
          ii -= 1
          idx(ii) += 1
          toClose += 1
        }
        writer.write("]" * toClose)
        if (it.hasNext()) {
          writer.write(",\n")
          writer.write(" " * (shape.size - toClose))
          writer.write("[" * toClose)
        }
        comma = false
      }
      writer.write("]")
    }
  }

  /** Utility function to write a NArray of the given dtypeStr to a writer
    */
  def writeNArrayWithDtypeStr(array: NArray, dtypeStr: String, writer: Writer): Unit = {
    val writeEl = dtypeStr match {
      case "f" | "f64" =>
        { (it: NIndexIterator) =>
          val elStr = it.getDoubleNext().toString()
          writer.write(elStr)
        }
      case "f32" =>
        { (it: NIndexIterator) => writer.write(it.getFloatNext().toString()) }
      case "i" | "i32" =>
        { (it: NIndexIterator) => writer.write(it.getIntNext().toString()) }
      case "i64" | "r" =>
        { (it: NIndexIterator) => writer.write(it.getLongNext().toString()) }
      case "b" =>
        { (it: NIndexIterator) => writer.write(it.getByteNext().toString()) }
      case "B" | "C" | "D" =>
        { (it: NIndexIterator) => JsonUtils.dumpString(it.next().toString(), writer) }
    }
    writeNArray(array, writeEl, writer)
  }

  /*def apply(
    metaEnv: MetaInfoEnv,
    outF: Writer
  ): JsonWriterBackend = {
    //val (sectionManagers, metaDataManagers) = CachingBackend

    //new JsonWriterBackend(metaEnv, sectionManagers, metaDataManagers, outF)
  }*/

  object WritingStatus extends Enumeration {
    type WritingStatus = Value
    val WrittenNone, WrittenHeader, InTopSectionList, WrittenRootSectionHeader, InRootSectionSections, AtEnd = Value
  }

  class JsonWriterException(
    msg: String, what: Throwable = null
  ) extends Exception(msg, what)

}

/** Backend that outputs the parsed data as nomadinfo.json
  */
class JsonWriterBackend(
  val metaInfoEnv: MetaInfoEnv,
  val outF: java.io.Writer,
  rootSections: Set[String] = Set("section_run"),
  unbundleFirstLevel: Boolean = true
) extends ParserBackendExternal {
  import JsonWriterBackend.WritingStatus._
  import JsonWriterBackend.JsonWriterException

  var mainFileUri: Option[String] = None
  var parserInfo: JValue = JNothing
  var parserStatus: Option[ParseResult.Value] = None
  var parserErrors: JValue = JNothing

  var openRootSectionName: String = ""
  var openRootSectionGIndex: Long = -1
  var openRootSection: Option[CachingBackend.CachingSection] = None
  var rootSectionValuesWritten: Set[String] = Set()
  var writingStatus: WritingStatus = WrittenNone

  val standaloneSections: Set[String] = if (unbundleFirstLevel) {
    metaInfoEnv.allNames.filter{(name: String) =>
      metaInfoEnv.metaInfoRecordForName(name).get.kindStr == "type_section" &&
      !rootSections.intersect(metaInfoEnv.rootAnchestorsOfType("type_section", name)).isEmpty
    }.toSet
  } else {
    Set()
  }

  val onCloseCallbacks: Map[String,Seq[CachingBackend.SectionCallback]] = if (rootSections.isEmpty) {
    metaInfoEnv.allNames.toSet.filter{ (name: String) =>
      metaInfoEnv.metaInfoRecordForName(name).get.kindStr == "type_section"
    }.map{ (name: String) =>
      name -> Seq(this.onCloseStandaloneSection _)
    }(breakOut)
  } else {
    standaloneSections.map{ (name: String) =>
      name -> Seq(this.onCloseStandaloneSection _)}.toMap ++ rootSections.map{ (name: String) =>
      if (standaloneSections.isEmpty)
        name -> Seq(this.onCloseStandaloneSection _)
      else
        name -> Seq(this.onCloseRootSection _)
    }.toMap
  }

  val onOpenCallbacks: Map[String,Seq[CachingBackend.SectionCallback]] = {
    rootSections.map{ (name: String) =>
      name -> Seq(this.onOpenRootSection _)
    }(breakOut)
  }

  val cachingBackend = CachingBackend(metaInfoEnv,
    cachingLevelForMetaName = rootSections.union(standaloneSections).map(_ -> CachingBackend.CachingLevel.CacheSubvalues)(breakOut),
    defaultSectionCachingLevel = if (rootSections.isEmpty) CachingBackend.CachingLevel.CacheSubvalues else CachingBackend.CachingLevel.Cache,
    defaultDataCachingLevel = CachingBackend.CachingLevel.Cache,
    superBackend = None,
    onCloseCallbacks = onCloseCallbacks,
    onOpenCallbacks = onOpenCallbacks
  )

  /** Started a parsing session
    */
  override def startedParsingSession(
    mainFileUri: Option[String],
    parserInfo: JValue,
    parserStatus: Option[ParseResult.Value] = None,
    parserErrors: JValue = JNothing
  ): Unit = {
    this.mainFileUri = mainFileUri
    this.parserInfo = parserInfo
    this.parserStatus = parserStatus
    this.parserErrors = parserErrors

    writingStatus match {
      case WrittenNone => ()
      case AtEnd => outF.write(", ")
      case _ =>
        throw new JsonWriterException(s"unexpected state $writingStatus in JsonWriter")
    }
    outF.write("""{
  "type": "nomad_info_1_0""")
    mainFileUri match {
      case Some(uri) =>
        outF.write(""",
  "mainFileUri": """)
        JsonUtils.dumpString(uri, outF)
      case None => ()
    }
    parserInfo match {
      case JNothing => ()
      case _ =>
        outF.write(""",
  "parserInfo": """)
        JsonUtils.prettyWriter(parserInfo, outF, 2)
    }
    parserStatus match {
      case Some(status) =>
        outF.write(""",
  "parserStatus": """)
        JsonUtils.dumpString(status.toString(), outF)
      case None => ()
    }
    parserErrors match {
      case JNothing => ()
      case _ =>
        outF.write(""",
  "parserErrors": """)
        JsonUtils.prettyWriter(parserErrors, outF, 2)
    }
    outF.write(""",
  "sections": [
    """)
    writingStatus = WrittenHeader
  }

  /** Opens a root section (i.e. a section that contains unbundled sections)
    */
  def onOpenRootSection(gBackend: CachingBackend, sectionManager: CachingBackend.CachingSectionManager, gIndex: Long, section: Option[CachingBackend.CachingSection]): Unit = {
    section match {
      case Some(s) => ()
      case None =>
        throw new JsonWriterException(s"Internal error: open of non cached root section ${sectionManager.metaInfo.name} $gIndex")
    }
    writingStatus match {
      case WrittenHeader => ()
      case InTopSectionList =>
        outF.write(", ")
      case WrittenRootSectionHeader | InRootSectionSections =>
        throw new JsonWriterException(s"nested root sections are not supported when unbundling first level, detected nesting of ${sectionManager.metaInfo.name} in $openRootSectionName $openRootSectionGIndex")
      case WrittenNone | AtEnd =>
        throw new JsonWriterException(s"nested root sections are not supported when unbundling first level, detected nesting of ${sectionManager.metaInfo.name} in $openRootSectionName $openRootSectionGIndex")
    }
    openRootSectionName = sectionManager.metaInfo.name
    openRootSectionGIndex = gIndex
    openRootSection = section
    outF.write(s"""{
    "type": "nomad_section_1_0",
    "gIndex": $gIndex,
    "name": "${sectionManager.metaInfo.name}"""")
    writingStatus = WrittenRootSectionHeader
  }

  /** Close a standalone section (i.e. a section that should be written out
    * and not cached in its supersection)
    */
  def onCloseRootSection(gBackend: CachingBackend, sectionManager: CachingBackend.CachingSectionManager, gIndex: Long, section: Option[CachingBackend.CachingSection]): Unit = {
    writingStatus match {
      case WrittenRootSectionHeader =>
      case InRootSectionSections =>
        outF.write("]")
      case _ =>
        throw new JsonWriterException(s"close of root section ${sectionManager.metaInfo.name} while in writingStatus $writingStatus")
    }
    if (sectionManager.metaInfo.name != openRootSectionName || gIndex != openRootSectionGIndex)
      throw new JsonWriterException(s"overlapping root sections are not supported when unbundling first level, detected overlap of ${sectionManager.metaInfo.name} $gIndex in $openRootSectionName $openRootSectionGIndex")
    section match {
      case Some(sect) =>
        writeOutSubvalues(
          section = sect,
          indent = 4,
          excludeMetaNames = rootSectionValuesWritten)
        outF.write("\n  }")
      case None =>
        throw new JsonWriterException(s"supressed root section ${sectionManager.metaInfo.name}, either unsuppress (CacheLevel.CacheSubvalues), or remove from root sections")
    }
    openRootSectionName = ""
    openRootSectionGIndex = -1
    openRootSection = None
    writingStatus = InTopSectionList
  }

  /** Close a standalone section (i.e. a section that should be written out
    * and not cached in its supersection)
    */
  def onCloseStandaloneSection(gBackend: CachingBackend, sectionManager: CachingBackend.CachingSectionManager, gIndex: Long, section: Option[CachingBackend.CachingSection]): Unit = {
    val sect: CachingBackend.CachingSection = section match {
      case Some(s) => s
      case None    => return ()
    }
    var indent: Int = 4
    writingStatus match {
      case WrittenHeader =>
        writingStatus = InTopSectionList
      case InTopSectionList =>
        outF.write(", ")
      case WrittenRootSectionHeader =>
        val parents = metaInfoEnv.rootAnchestorsOfType("type_section", sectionManager.metaInfo.name)
        if (parents != Set(openRootSectionName))
          throw new JsonWriterException(s"overlapping root sections are not supported when unbundling first level, detected emit of ${sectionManager.metaInfo.name} inheriting from $parents in $openRootSectionName")
        openRootSection match {
          case Some(rootSect) =>
            val toExclude: Set[String] = rootSect.cachedSubSections.map{ case (k, _) => k }(breakOut)
            writeOutSubvalues(rootSect, indent = 4, excludeMetaNames = toExclude)
            outF.write(",\n    \"sections\": [")
          case None =>
            throw new JsonWriterException("no openRootSection when writingStatus is WrittenRootSectionHeader")
        }
        indent = 6
        writingStatus = InRootSectionSections
      case InRootSectionSections =>
        val parents = metaInfoEnv.rootAnchestorsOfType("type_section", sectionManager.metaInfo.name)
        if (parents != Set(openRootSectionName))
          throw new JsonWriterException(s"overlapping root sections are not supported when unbundling first level, detected emit of ${sectionManager.metaInfo.name} inheriting from $parents in $openRootSectionName")
        outF.write(", ")
        indent = 6
    }
    writeOutSection(sectionManager.metaInfo.name, sect, indent)
  }

  /** Finished a parsing session
    */
  override def finishedParsingSession(
    parserStatus: Option[ParseResult.Value],
    parserErrors: JValue = JNothing,
    mainFileUri: Option[String] = None,
    parserInfo: JValue = JNothing): Unit = {

    outF.write("]")

    if (this.mainFileUri.isEmpty) {
      this.mainFileUri = mainFileUri
      mainFileUri match {
        case Some(uri) =>
          outF.write(""",
  "mainFileUri": """)
          JsonUtils.dumpString(uri, outF)
        case None => ()
      }
    }
    if (this.parserInfo.toSome.isEmpty) {
      this.parserInfo = parserInfo
      parserInfo match {
        case JNothing => ()
        case _ =>
          outF.write(""",
  "parserInfo": """)
          JsonUtils.prettyWriter(parserInfo, outF, 2)
      }
    }
    if (this.parserStatus.isEmpty) {
      this.parserStatus = parserStatus
      parserStatus match {
        case Some(status) =>
          outF.write(""",
  "parserStatus": """)
          JsonUtils.dumpString(status.toString(), outF)
        case None => ()
      }
    }
    if (this.parserErrors.toSome.isEmpty) {
      this.parserErrors = parserErrors
      parserErrors match {
        case JNothing => ()
        case _ =>
          outF.write(""",
  "parserErrors": """)
          JsonUtils.prettyWriter(parserErrors, outF, 2)
      }
    }
    outF.write("""
}""")
    outF.flush()
  }

  def writeOutSection(metaName: String, section: CachingBackend.CachingSection, indent: Int): Unit = {
    val baseIndenter = new JsonUtils.ExtraIndenter(indent, outF)
    baseIndenter.write(s"""{
  "type": "nomad_section_1_0",
  "name": "$metaName",
  "gIndex": ${section.gIndex},
  "references": {""")
    var refComma = false
    for ((sectionName, gId) <- GenericBackend.firstSuperSections(metaInfoEnv, metaName).zip(section.references)) {
      if (refComma)
        outF.write(", ")
      else
        refComma = true
      baseIndenter.write(s"""
    "$sectionName": $gId""")
    }
    outF.write("}")
    writeOutSubvalues(section, indent + 2, Set())
    baseIndenter.write("\n}")
  }

    /** writes out the values stored in section (inserting commas, finishing without comma)
      */
  def writeOutSubvalues(section: CachingBackend.CachingSection, indent: Int, excludeMetaNames: Set[String]): Unit = {
    val baseIndenter = new JsonUtils.ExtraIndenter(indent, outF)
    for ((metaName, vals) <- section.cachedSimpleValues.filter{case (k, _) => !excludeMetaNames(k)}) {
      val repeats: Boolean = metaInfoEnv.metaInfoRecordForName(metaName).get.repeats.getOrElse(false)
      if (repeats) {
        baseIndenter.write(s""",
"$metaName": """)
        JsonUtils.prettyWriter(JArray(vals.toList), outF, indent + 2)
      } else if (vals.size == 1) {
        vals.head match {
          case JNothing =>
            ()
          case _ =>
            baseIndenter.write(s""",
"$metaName": """)
            JsonUtils.prettyWriter(vals.head, outF, indent + 2)
        }
      } else if (vals.size != 0) {
        throw new JsonUtils.InvalidValueError(metaName, "JsonWriterBackend", JsonUtils.prettyStr(JArray(vals.toList)), "Non repeating value should have one value")
      }
    }
    for ((metaName, vals) <- section.cachedArrayValues.filter{case (k, _) => !excludeMetaNames(k)}) {
      val metaInfo = metaInfoEnv.metaInfoRecordForName(metaName).get
      val repeats: Boolean = metaInfo.repeats.getOrElse(false)
      if (repeats) {
        baseIndenter.write(s""",
"$metaName": """)
        val innerIndenter = new JsonUtils.ExtraIndenter(indent + 4, outF)
        outF.write("[")
        var arrComma: Boolean = false
        for (value <- vals) {
          if (arrComma)
            outF.write(",")
          else
            arrComma = true
          innerIndenter.write("\n")
          JsonWriterBackend.writeNArrayWithDtypeStr(value, metaInfo.dtypeStr.get, innerIndenter)
        }
        baseIndenter.write("\n    ]")
      } else if (vals.size == 1) {
        baseIndenter.write(s""",
"$metaName": """)
        val innerIndenter = new JsonUtils.ExtraIndenter(indent + 2, outF)
        JsonWriterBackend.writeNArrayWithDtypeStr(vals(0), metaInfo.dtypeStr.get, innerIndenter)
      } else if (vals.size != 0) {
        throw new JsonUtils.InvalidValueError(metaName, "JsonWriterBackend", s"#${vals.size}", "Non repeating value should have one value, either make it repeating or ensure it does not repeat.")
      }
    }
    for ((metaName, vals) <- section.cachedSubSections.filter{case (k, _) => !excludeMetaNames(k)}) {
      val metaInfo = metaInfoEnv.metaInfoRecordForName(metaName).get
      val repeats: Boolean = metaInfo.repeats.getOrElse(true)
      if (repeats) {
        baseIndenter.write(s""",
"$metaName": [""")
        var sectComma: Boolean = false
        for (value <- vals) {
          if (sectComma)
            outF.write(",")
          else
            sectComma = true
          writeOutSection(metaName, value, indent + 4)
        }
        outF.write("]")
      } else if (vals.size == 1) {
        baseIndenter.write(s""",
"$metaName": """)
        writeOutSection(metaName, vals.head, indent + 2)
      } else if (vals.size != 0) {
        throw new JsonUtils.InvalidValueError(metaName, "JsonWriterBackend", s"#${vals.size}", "Non repeating value should have one value, either make it repeating or ensure it does not repeat.")
      }
    }
  }

  def addArray(metaName: String,shape: Seq[Long],gIndex: Long): Unit = {
    cachingBackend.addArray(metaName, shape, gIndex)
  }

  def addRealValue(metaName: String,value: Double,gIndex: Long): Unit = {
    cachingBackend.addRealValue(metaName, value, gIndex)
  }

  def addValue(metaName: String,value: org.json4s.JValue,gIndex: Long): Unit = {
    cachingBackend.addValue(metaName, value, gIndex)
  }

  def closeSection(metaName: String,gIndex: Long): Unit = {
    cachingBackend.closeSection(metaName, gIndex)
  }

  def openSections(): Iterator[(String, Long)] = {
    cachingBackend.openSections
  }

  def sectionInfo(metaName: String,gIndex: Long): String = {
    cachingBackend.sectionInfo(metaName, gIndex)
  }

  def setArrayValues(metaName: String,values: ucar.ma2.Array,offset: Option[Seq[Long]],gIndex: Long): Unit = {
    cachingBackend.setArrayValues(metaName, values, offset, gIndex)
  }

  override def addArrayValues(metaName: String,values: ucar.ma2.Array, gIndex: Long): Unit = {
    cachingBackend.addArrayValues(metaName, values, gIndex)
  }

  def setSectionInfo(metaName: String,gIndex: Long,references: Map[String,Long]): Unit = {
    cachingBackend.setSectionInfo(metaName, gIndex, references)
  }

  def openSectionWithGIndex(metaName: String,gIndex: Long): Unit = {
    cachingBackend.openSectionWithGIndex(metaName, gIndex)
  }
}
