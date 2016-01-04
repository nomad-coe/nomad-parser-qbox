package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import ucar.ma2.{IndexIterator => NIndexIterator}
import ucar.ma2.DataType
import ucar.ma2.ArrayString
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import java.io.Writer

object JsonWriterBackend {
  /** section that writes all its values as a single json dictionary
    *
    * If standalone it will wite out the values immediately, otherwise tries to be
    * outputted with its parent
    */
  class JsonWriterSectionManager(
    metaInfo: MetaInfoRecord,
    parentSectionNames: Array[String],
    lastSectionGIndex0: Long = -1,
    openSections0: Map[Long, CachingBackend.CachingSection] = Map(),
    standalone: Boolean = true
  ) extends CachingBackend.CachingSectionManager(
    metaInfo, parentSectionNames, lastSectionGIndex0, openSections0
  ) {
    override def onClose(gBackend: GenericBackend, gIndex: Long, section: CachingBackend.CachingSection): Unit = {
      val backend = gBackend match {
        case b: JsonWriterBackend => b
        case _ => throw new GenericBackend.InternalErrorException("expected JsonWriterBackend")
      }
      if (!standalone && section.references.size == 1) {
        backend.sectionManagers(parentSectionNames(0)).openSections.get(section.references(0)) match {
          case Some(openS) =>
            openS.addSubsection(metaInfo, section)
            return
          case None => ()
        }
      }
      backend.writeOut(metaInfo.name, section)
    }
  }

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
      while (idx(0) == shape(0)) {
        if (comma)
          writer.write(", ")
        else
          comma = true
        writeNextEl(it)
        var ii: Int = shape.size - 1
        var toClose: Int = 0
        idx(ii) += 1
        while (ii > 0 && idx(ii) == shape(ii)) {
          idx(ii) = 0
          ii -= 1
          idx(ii) += 1
          toClose += 1
        }
        if (toClose > 0) {
          writer.write("]" * toClose)
          if (it.hasNext()) {
            writer.write(",\n")
            writer.write(" " * (shape.size - toClose))
            writer.write("[" * toClose)
          }
          comma = false
        }
      }
      writer.write("]")
    }
  }

  /** Utility function to write a NArray of the given dtypeStr to a writer
    */
  def writeNArrayWithDtypeStr(array: NArray, dtypeStr: String, writer: Writer): Unit = {
    val writeEl = dtypeStr match {
      case "f" | "f64" =>
        { (it: NIndexIterator) => writer.write(it.getDoubleNext().toString()) }
      case "f32" =>
        { (it: NIndexIterator) => writer.write(it.getFloatNext().toString()) }
      case "i" | "i32" =>
        { (it: NIndexIterator) => writer.write(it.getIntNext().toString()) }
      case "i64" =>
        { (it: NIndexIterator) => writer.write(it.getLongNext().toString()) }
      case "b" =>
        { (it: NIndexIterator) => writer.write(it.getByteNext().toString()) }
      case "B" | "C" | "D" =>
        { (it: NIndexIterator) => JsonUtils.dumpString(it.next().toString(), writer) }
    }
    writeNArray(array, writeEl, writer)
  }

  def apply(
    metaEnv: MetaInfoEnv,
    sectionFactory: (MetaInfoEnv, MetaInfoRecord, Array[String]) => CachingBackend.CachingSectionManager = CachingBackend.cachingSectionFactory,
    dataFactory: (MetaInfoEnv, MetaInfoRecord, CachingBackend.CachingSectionManager) => GenericBackend.MetaDataManager = CachingBackend.cachingDataFactory(Set()),
    outF: Writer
  ): JsonWriterBackend = {
    val (sectionManagers, metaDataManagers) = CachingBackend.instantiateManagers(metaEnv, sectionFactory, dataFactory)

    new JsonWriterBackend(metaEnv, sectionManagers, metaDataManagers, outF)
  }

}

/** Backend that outputs the parsed data as nomadinfo.json
  */
class JsonWriterBackend(
  metaInfoEnv: MetaInfoEnv,
  sectionManagers: Map[String, CachingBackend.CachingSectionManager],
  metaDataManagers: Map[String, GenericBackend.MetaDataManager],
  val outF: java.io.Writer
) extends CachingBackend(metaInfoEnv, sectionManagers, metaDataManagers) {

  var writeComma: Boolean = false

  /** Started a parsing session
    */
  override def startedParsingSession(
    mainFileUri: Option[String],
    parserInfo: JValue,
    parserStatus: Option[ParseResult.Value] = None,
    parserErrors: JValue = JNothing): Unit = {
    super.startedParsingSession(mainFileUri, parserInfo, parserStatus, parserErrors)
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
        JsonUtils.dumpString(parserStatus.toString(), outF)
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
  }

  /** Finished a parsing session
    */
  override def finishedParsingSession(
    parserStatus: Option[ParseResult.Value],
    parserErrors: JValue = JNothing,
    mainFileUri: Option[String] = None,
    parserInfo: JValue = JNothing): Unit = {
    val session = parsingSession match {
      case Some(pSession) => pSession
      case None => throw new GenericBackend.InternalErrorException(s"Mismatched finished parsing of $mainFileUri while no parsing session were open")
    }
    val mainFileUriNotWritten = session.mainFileUri.isEmpty
    val parserInfoNotWritten = session.parserInfo.toSome.isEmpty
    val parserStatusNotWritten = session.parserStatus.isEmpty
    val parserErrorsNotWritten = session.parserErrors.toSome.isEmpty

    super.finishedParsingSession(parserStatus, parserErrors, mainFileUri, parserInfo)
    outF.write("]")

    if (mainFileUriNotWritten) {
      mainFileUri match {
        case Some(uri) =>
          outF.write(""",
  "mainFileUri": """)
          JsonUtils.dumpString(uri, outF)
        case None => ()
      }
    }
    if (parserInfoNotWritten) {
      parserInfo match {
        case JNothing => ()
        case _ =>
          outF.write(""",
  "parserInfo": """)
          JsonUtils.prettyWriter(parserInfo, outF, 2)
      }
    }
    if (parserStatusNotWritten) {
      parserStatus match {
        case Some(status) =>
          outF.write(""",
  "parserStatus": """)
          JsonUtils.dumpString(parserStatus.toString(), outF)
        case None => ()
      }
    }
    if (parserErrorsNotWritten) {
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
  }

  def writeOut(metaName: String, section: CachingBackend.CachingSection): Unit = {
    if (writeComma)
      outF.write(", ")
    else
      writeComma = true
    writeOutBase(metaName, section, 4)
  }

  def writeOutBase(metaName: String, section: CachingBackend.CachingSection, indent: Int): Unit = {
    val baseIndenter = new JsonUtils.ExtraIndenter(indent, outF)
    baseIndenter.write(s"""{
  "type": "nomad_section_1_0",
  "gIndex": ${section.gIndex},
  "references": {""")
    var refComma = false
    for ((sectionName, gId) <- GenericBackend.firstSuperSections(metaInfoEnv, metaName).zip(section.references)) {
      if (refComma)
        outF.write(", ")
      else
        refComma = true
      baseIndenter.write("""
    "$sectionName": $gIndex""")
    }
    outF.write("]")
    for ((metaName, vals) <- section.cachedSimpleValues) {
      baseIndenter.write(""",
"$metaName": """)
      val repeats: Boolean = metaInfoEnv.metaInfoRecordForName(metaName).get.repeats.getOrElse(false)
      if (repeats)
        JsonUtils.prettyWriter(JArray(vals.toList), outF, indent + 2)
      else if (vals.size != 1)
        throw new JsonUtils.InvalidValueError(metaName, "JsonWriterBackend", JsonUtils.prettyStr(JArray(vals.toList)), "Non repeating value should have one value")
      else
        JsonUtils.prettyWriter(vals(0), outF, indent + 2)
    }
    for ((metaName, vals) <- section.cachedArrayValues) {
      baseIndenter.write(""",
"$metaName": """)
      val metaInfo = metaInfoEnv.metaInfoRecordForName(metaName).get
      val repeats: Boolean = metaInfo.repeats.getOrElse(false)
      if (repeats) {
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
      } else if (vals.size != 1) {
        throw new JsonUtils.InvalidValueError(metaName, "JsonWriterBackend", s"#${vals.size}", "Non repeating value should have one value")
      } else {
        val innerIndenter = new JsonUtils.ExtraIndenter(indent + 2, outF)
        JsonWriterBackend.writeNArrayWithDtypeStr(vals(0), metaInfo.dtypeStr.get, innerIndenter)
      }
    }
    for ((metaName, vals) <- section.cachedSubSections) {
      baseIndenter.write(""",
"$metaName": [""")
      var sectComma: Boolean = false
      for (value <- vals) {
        if (sectComma)
          outF.write(",")
        else
          sectComma = true
        writeOutBase(metaName, value, indent + 4)
      }
      outF.write("]")
    }
    baseIndenter.write("\n}")
  }

}
