package eu.nomad_lab.meta
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import org.json4s.DefaultFormats
import java.io.{FileInputStream, InputStream}
import scala.collection.mutable
import scala.collection.breakOut
import scala.util.control.NonFatal
import com.typesafe.scalalogging.StrictLogging
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.CompactSha


/** a MetaInfoEnv that simply stores all MetaInfoRecords and its dependencies
  */
class SimpleMetaInfoEnv(
  val name: String,
  val description: String,
  val source:  JObject,
  val nameToGid: Map[String, String],
  val gidToName: Map[String, String],
  val metaInfosMap: Map[String, MetaInfoRecord],
  val dependencies: Seq[MetaInfoEnv],
  val kind: MetaInfoEnv.Kind.Kind) extends MetaInfoEnv {

  /** Tries to get value from dependencies
    *
    * basically dependencies.flatMap(f).headOption with guaranteed quick return
    */
  def firstFromDeps[T](f: MetaInfoEnv => Option[T]): Option[T] = {
    for (d <- dependencies) {
      val value = f(d)
      if (!value.isEmpty)
        return value
    }
    None
  }

  /** The names of the meta info contained directly in this environment,
    * no dependencies
    */
  def names: Iterator[String] = metaInfosMap.keysIterator

  /** The meta infos stored (no dependencies)
    */
  def metaInfos(selfGid: Boolean, superGids: Boolean): Iterator[MetaInfoRecord] = {
    metaInfosMap.valuesIterator.map{ metaInfo: MetaInfoRecord =>
      fixMetaInfo(metaInfo, selfGid, superGids)
    }
  }

  /** gids of the meta infos contained in this environment (no dependencies)
    */
  def gids: Seq[String] = metaInfosMap.keys.flatMap{ nameToGid.get(_) }(breakOut)

  /** returns all versions defined (might contain duplicates!)
    */
  def allEnvs: Iterator[MetaInfoEnv] = {
    dependencies.foldLeft[Iterator[MetaInfoEnv]](Iterator(this))( _ ++ _.allEnvs )
  }

  /** returns the versions that contain that gid
    *
    * If recursive is true, inclusion through a dependency is also
    * considered.
    */
  def versionsForGid(gid: String, recursive: Boolean = false): Iterator[String] = {
    if (!nameForGid(gid, recursive).isEmpty)
      Iterator(this.name)
    else
      Iterator()
  }

  /** Returns the gid of the MetaInfoRecord with the given name
    *
    * *Not* lazy, requires the gid to be already calculated.
    * If recursive is false only gids of MetaInfoRecords directly contained
    * are returned, no dependencies
    */
  def gidForName(name: String, recursive: Boolean = true): Option[String] = {
    nameToGid.get(name) match {
      case Some(gid) =>
        if (!recursive && ! metaInfosMap.contains(name))
          None
        else
          Some(gid)
      case None =>
        if (recursive) {
          firstFromDeps(_.gidForName(name, recursive))
        } else {
          None
        }
    }
  }

  /** Returns the name of the MetaInfoRecord corresponding to the given gid
    *
    * *not* lazy, requires the gid to be already calculated
    * if recursive is false only names directly contained are returned, no dependencies
    */
  def nameForGid(gid: String, recursive: Boolean = true): Option[String] = {
    gidToName.get(gid) match {
      case Some(name) =>
        if (!recursive && ! metaInfosMap.contains(name))
          None
        else
          Some(name)
      case None =>
        if (recursive) {
          firstFromDeps(_.nameForGid(gid, recursive))
        } else {
          None
        }
    }
  }

  /** Completes the meta info with gid and superGids as requested
    *
    * Does not trust the values in the metaInfo
    */
  def fixMetaInfo(metaInfo: MetaInfoRecord, selfGid: Boolean, superGids: Boolean): MetaInfoRecord = {
    val gid = (
      if (selfGid)
        gidForName(metaInfo.name, recursive = true).getOrElse("")
      else
        ""
    )
    val sGids = (
      if (superGids && !metaInfo.superNames.isEmpty)
        metaInfo.superNames.map(gidForName(_, recursive = true).getOrElse(""))
      else
        Seq()
    )
    metaInfo.copy(gid = gid, superGids = sGids)
  }

  /** Returns the metaInfoRecord for the given name
    *
    * gid and super gids are set in the returned record only if requested.
    */
  def metaInfoRecordForName(name: String, selfGid: Boolean = false, superGids: Boolean = false): Option[MetaInfoRecord] = {
    metaInfosMap.get(name) match {
      case Some(baseVal) => Some(fixMetaInfo(baseVal, selfGid, superGids))
      case None =>
        firstFromDeps(_.metaInfoRecordForName(name, selfGid, superGids))
    }
  }

  /** Returns the metaInfoRecord for the given gid
    *
    * gid should have been calculated to be found.
    * gid and super gids are set in the returned record only if requested.
    * If gid was calculated locally, but not in the dependecy that defines it
    * it is not returned as it might be the wrong one if one has multiples
    * definitions with the same name in the dependencies (something that should be avoided).
    */
  def metaInfoRecordForGid(gid: String, selfGid: Boolean = false, superGids: Boolean = false): Option[MetaInfoRecord] = {
    nameForGid(gid, recursive = false) match {
      case Some(name) =>
        metaInfoRecordForName(name, selfGid, superGids)
      case None       => firstFromDeps(_.metaInfoRecordForGid(gid, selfGid, superGids))
    }
  }
}

object SimpleMetaInfoEnv extends StrictLogging {
  implicit val formats = DefaultFormats + new MetaInfoRecordSerializer;

  /** Evaluates the gid of the given meta info
    *
    * Requires that all superNames have gids calculated in nameToGid.
    */
  def evalGid(
    metaInfo: MetaInfoRecord,
    nameToGid: scala.collection.Map[String,String]): String = {
    val sha = CompactSha()
    JsonUtils.normalizedOutputStream(metaInfo.copy(
      gid="",
      superGids=metaInfo.superNames.map(nameToGid(_))).toJValue(),
      sha.outputStream)
    sha.gidStr("p") // use gidAscii?
  }

  /** Calculates the Gid of name, resolving all dependencies and calculating their gid
    * if required.
    *
    * nameToGidsCache will be updated with all the gids calculated.
    */
  def calculateGid(
    name: String,
    nameToGidCache: mutable.Map[String,String],
    metaInfos: Map[String, MetaInfoRecord],
    dependencies: Seq[MetaInfoEnv],
    context: String,
    precalculated: Boolean = false): String = {

    def firstMetaFromDeps(n: String): Option[MetaInfoRecord] = {
      for (d <- dependencies) {
        val value = d.metaInfoRecordForName(n)
        if (!value.isEmpty)
          return value
      }
      None
    }

    nameToGidCache.get(name) match {
      case Some(v) =>
        v
      case None =>
        if (precalculated) {
          throw new GidNotPrecalculatedError(name, context)
        } else {
          val inProgress = mutable.ListBuffer[String]()
          var hasPending: Boolean = false
          val toDo = mutable.ListBuffer[String](name)

          for (i <- 1 to 2 ) {
            while (!toDo.isEmpty) {
              var now: String = ""
              if (!hasPending && !inProgress.isEmpty) {
                now = inProgress.last
                inProgress.trimEnd(1)
              } else {
                now = toDo.last
                toDo.trimEnd(1)
              }
              hasPending = false
              val nowMeta = metaInfos.get(now) match {
                case Some(meta) => meta
                case None => {
                  firstMetaFromDeps(now) match {
                    case Some(meta) => meta
                    case None => throw new MetaInfoEnv.DependsOnUnknownNameException(
                      context, name, now)
                  }
                }
              }
              for (superName <- nowMeta.superNames) {
                if (!nameToGidCache.contains(superName)) {
                  hasPending = true
                  if (toDo.contains(superName))
                    toDo -= superName
                  if (inProgress.contains(superName))
                    throw new MetaInfoEnv.MetaInfoCircularDepException(
                      context, name, superName, inProgress)
                  toDo += superName
                }
              }
              if (!hasPending) {
                val gidNow = evalGid(nowMeta, nameToGidCache)
                nameToGidCache += (now -> gidNow)
                if (inProgress.contains(now))
                  inProgress -= now
              } else {
                if (inProgress.contains(now))
                  throw new MetaInfoEnv.MetaInfoCircularDepException(
                    context, name, now, inProgress)
                inProgress += now
              }
            }
            toDo ++= inProgress
            inProgress.clear()
          }
          nameToGidCache(name)
        }
    }
  }

  /** initializes a SimpleMetaInfoEnv from a path of the filesystem
    */
  def fromFilePath(filePath: String, dependencyResolver: DependencyResolver): SimpleMetaInfoEnv = {
    val f = new FileInputStream(filePath)
    fromInputStream(
      stream = f,
      name = filePath,
      source = JObject(List(JField("path", JString(filePath)))),
      dependencyResolver = dependencyResolver)
  }

  /** initializes a SimpleMetaInfoEnv with an input stream containing UTF-8 encoded json
    */
  def fromInputStream(
    stream: InputStream,
    name: String,
    source: JObject,
    dependencyResolver: DependencyResolver,
    keepExistingGidsValues: Boolean = true,
    ensureGids:Boolean = true
  ): SimpleMetaInfoEnv = {
    val metaInfoJson = JsonUtils.parseInputStream(stream)
    metaInfoJson \ "type" match {
      case JString(s) =>
        val typeRe = "^nomad_meta_info_([0-9]+)_([0-9])$".r
        typeRe.findFirstMatchIn(s) match {
          case Some(m) =>
            val major = m.group(1).toInt
            val minor = m.group(2).toInt
            if (major != 1)
              throw new MetaInfoEnv.ParseException(s"cannot load $name because it uses a different major version of the format ($s, expected nomad_meta_info_1_0)")
            else if (minor != 0)
              logger.warn("found newer minor revision while loading $name ($s vs nomad_meta_info_1_0), loading.")
          case None =>
            throw new MetaInfoEnv.ParseException(s"unexpected type '$s' while loading '$name'")
        }
      case JNothing =>
        logger.warn(s"missing type while loading $name (expected nomad_meta_info_1_0), loading.")
      case invalidJson =>
        throw new MetaInfoEnv.ParseException(s"unexpected type '${JsonUtils.prettyStr(invalidJson)}' while loading '$name'")
    }
    val description = metaInfoJson \ "description" match {
      case JString(s)   => s
      case JArray(arr)  =>
        val sb = new StringBuilder()
        arr.foreach{
          case JString(s)  => sb ++= (s)
          case JNothing    => ()
          case invalidJson => throw new MetaInfoEnv.ParseException(
            s"unexpected value for description while loading '$name', expected either a string or an array of strings, got '${JsonUtils.prettyStr(invalidJson)}'")
        }
        sb.toString
      case JNothing     => ""
      case invalidJson  => throw new MetaInfoEnv.ParseException(
          s"unexpected value for description while loading '$name', expected an array, got '${JsonUtils.prettyStr(invalidJson)}'")
    }
    val jsonList = metaInfoJson \ "metaInfos" match {
      case JArray(arr)  => JArray(arr)
      case JObject(obj) => JArray(JObject(obj) :: Nil) // disallow?
      case JNothing     => JArray(Nil)
      case invalidJson  => throw new MetaInfoEnv.ParseException(
          s"unexpected value for metaInfos while loading '$name', expected an array, got '${JsonUtils.prettyStr(invalidJson)}'")
    }
    val dependencies = metaInfoJson \ "dependencies" match {
      case JArray(arr)  => JArray(arr)
      case JObject(obj) => JArray(JObject(obj) :: Nil) // disallow?
      case JNothing     => JArray(Nil)
      case invalidJson  => throw new MetaInfoEnv.ParseException(
        s"unexpected value for dependencies while loading '$name', expected an array, got '${JsonUtils.prettyStr(invalidJson)}'")
    }
    fromJsonList(
      name = name,
      description = description,
      metaInfos = jsonList,
      dependencies = dependencies,
      source = source,
      dependencyResolver = dependencyResolver,
      keepExistingGidsValues = keepExistingGidsValues,
      ensureGids = ensureGids,
      kind = MetaInfoEnv.Kind.File
    )
  }

  /** Initializes with a json list
    *
    * Should probabply be rewritten using strong types, not JValues, would give better error messages.
    * Use apply instead?
    */
  def fromJsonList(name: String, description: String, source: JObject, metaInfos: JArray, dependencies: JArray, dependencyResolver: DependencyResolver,
    keepExistingGidsValues: Boolean = true, ensureGids: Boolean = true, kind: MetaInfoEnv.Kind.Value
  ): SimpleMetaInfoEnv = {
    var deps: List[MetaInfoEnv] = Nil
    val metaInfoCache = new mutable.HashMap[String, MetaInfoRecord]
    val nameToGid = new mutable.HashMap[String, String]
    implicit val formats = DefaultFormats + new MetaInfoRecordSerializer

    for (nDep <- dependencies.arr) {
      nDep match {
        case JObject(obj) =>
          try {
            deps = dependencyResolver.resolveDependency(source, JObject(obj)) :: deps
          } catch {
            case NonFatal(e) =>
              throw new MetaInfoEnv.ParseException(s"Error loading $name processing dependency ${JsonUtils.prettyStr(nDep)}", e)
          }
        case _ =>
          throw new MetaInfoEnv.ParseException(s"expected an object as dependency, not ${JsonUtils.prettyStr(nDep)}")
      }
    }

    for (jsonObj <- metaInfos.arr) {
      try {
        val metaInfo = jsonObj.extract[MetaInfoRecord]
        if (metaInfoCache.contains(metaInfo.name))
          throw new MetaInfoEnv.DuplicateNameException(metaInfo.name, metaInfo, metaInfoCache(metaInfo.name))
        metaInfoCache += (metaInfo.name -> metaInfo)
        if (keepExistingGidsValues && !metaInfo.gid.isEmpty)
          nameToGid += (metaInfo.name -> metaInfo.gid)
      } catch {
        case NonFatal(e) =>
          throw new MetaInfoEnv.ParseException(s"Error loading $name processing metaInfo ${JsonUtils.prettyStr(jsonObj)}", e)
      }
    }
  val metaInfosMap = metaInfoCache.toMap
    val dependenciesSeq = deps.toSeq
    if (ensureGids) {
      for ((name, metaInfo) <- metaInfoCache) {
        if (!nameToGid.contains(name))
          calculateGid(name, nameToGid, metaInfosMap, dependenciesSeq, name)
      }
    }
    new SimpleMetaInfoEnv(
      name = name,
      description = description,
      source = source,
      nameToGid = nameToGid.toMap,
      gidToName = nameToGid.map{ case (name, gid) => (gid, name)}(breakOut),
      metaInfosMap = metaInfosMap,
      dependencies = dependenciesSeq,
      kind = kind)
  }

  /** a value that was expected to have a precalculated Gid did not have it
    */
  case class GidNotPrecalculatedError(
    name: String,
    context: String
  ) extends Exception(
    s"gid of $name was not precomputated in $context") { }

}
