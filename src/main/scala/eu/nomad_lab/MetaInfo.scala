package eu.nomad_lab
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import org.json4s.{DefaultFormats, CustomSerializer}
//import org.jooq;
import java.io.{FileInputStream, InputStream}
import java.nio.file.{Path, Paths}
import scala.collection.mutable
import scala.collection.breakOut
import scala.util.control.NonFatal

/** Represents a piece of nomad meta info referring to other meta info by name.
  *
  * Can be interpreted within a context [[eu.nomad_lab.MetaInfoEnv]], but matches the
  * preferred way one describes it in a json file.
  */
case class MetaInfoRecord(
  val name: String,
  val kindStr: String,
  val description: String,
  val superNames:  Seq[String] = Seq(),
  val units:    Option[String] = None,
  val dtypeStr: Option[String] = None,
  val repeats: Option[Boolean] = None,
  val shape:  Option[Seq[Int]] = None,
  val gid:              String = "",
  val superGids:   Seq[String] = Seq(),
  val otherKeys:  List[JField] = Nil) {

  /** returns a JValue (json) representation of the current record
    *
    * It is possible to control if the extra arguments are output
    * just like other fields, as a sub dictionary or not at all
    */
  def toJValue(extraArgs: Boolean = true, inlineExtraArgs: Boolean = true): JValue = {
    import org.json4s.JsonDSL._;
    val baseObj = (
      ("name" -> name) ~
        ("gid" -> (if (gid.isEmpty) None else Some(gid))) ~
        ("kindStr" -> (if (kindStr == "DocumentContentType") None else Some(kindStr)))~
        ("description" -> description)~
        ("superNames" -> superNames)~
        ("superGids" -> (if (superGids.isEmpty) None else Some(superGids))) ~
        ("units", units)~
        ("dtypeStr", dtypeStr)~
        ("repeats", repeats)~
        ("shape", shape)
    );
    if (extraArgs) {
      if (inlineExtraArgs)
        JObject(baseObj.obj ++ otherKeys)
      else
        baseObj ~ ("extraArgs" -> JObject(otherKeys))
    } else {
      baseObj
    }
  }

  override def toString(): String = JsonUtils.prettyStr(toJValue())
}

object MetaInfoRecord {
  /** valid dtypes
    * f: generic float (not specified, default dependent on settings)
    * i: generic integer (not specified, default dependent on settings)
    * f32: 32 bit floating point (single precision)
    * i32: signed 32 bit integer
    * f64: 64 bit floating point (double precision)
    * i64: 64 bit signed integer
    * b: byte
    * c: unicode character
    * B: byte array (blob)
    * C: unicode string
    */
  final val dtypes = Seq("f", "i", "f32", "i32", "u32", "f64", "i64", "u64", "b", "c", "B", "C")
}

/** Json serialization to and deserialization support for MetaInfoRecord
  */
class MetaInfoRecordSerializer extends CustomSerializer[MetaInfoRecord](format => (
         {
           case JObject(obj) => {
             implicit val formats = DefaultFormats;
             var name: String = "";
             var gid: String = "";
             var kindStr: String = "DocumentContentType";
             var description: String = "";
             var superNames: Seq[String] = Seq();
             var superGids: Seq[String] = Seq();
             var units: Option[String] = None;
             var dtypeStr: Option[String] = None;
             var repeats: Option[Boolean] = None;
             var shape: Option[Seq[Int]] = None;
             var otherKeys: List[JField] = Nil;
             obj foreach {
               case JField("name", value) =>
                 name = value.extract[String]
               case JField("gid", value) =>
                 gid = value.extract[String]
               case JField("kindStr", value) =>
                 kindStr = value.extract[String]
               case JField("description", value) =>
                 description = value.extract[String]
               case JField("superNames", value) =>
                 superNames = value.extract[Seq[String]]
               case JField("superGids", value) =>
                 superGids = value.extract[Seq[String]]
               case JField("units", value) =>
                 units = value.extract[Option[String]]
               case JField("dtypeStr", value) =>
                 dtypeStr = value.extract[Option[String]]
               case JField("repeats", value) =>
                 repeats = value.extract[Option[Boolean]]
               case JField("shape", value) =>
                 shape = value.extract[Option[Seq[Int]]]
               case JField(key, value) =>
                 otherKeys = JField(key, value) +: otherKeys
             }
             if (name.isEmpty) throw new JsonUtils.MissingFieldError("name", "NomadMetaInfo")
             if (description.isEmpty) throw new JsonUtils.MissingFieldError("description", "NomadMetaInfo")
             if (!dtypeStr.isEmpty && !(MetaInfoRecord.dtypes contains dtypeStr.get))
               throw new JsonUtils.InvalidValueError("dtypeStr", "NomadMetaInfo", dtypeStr.get, MetaInfoRecord.dtypes.foldLeft(""){ _ + " " + _ })
             if (!superGids.isEmpty && superNames.length != superGids.length)
               throw new JsonUtils.InvalidValueError("superGids", "NomadMetaInfo", superGids.mkString("[",", ","]"), s"incompatible length with superNames ${superNames.mkString("[",",","]")}")

             new MetaInfoRecord(name, kindStr, description, superNames, units,
               dtypeStr, repeats, shape, gid, superGids, otherKeys)
           }
         },
         {
           case x: MetaInfoRecord => {
             x.toJValue()
           }
         }
       ))

/** Interface to a version of NomadMetaInfos i.e. a set of MetaInfoRecords in which the MetaInfoRecord.name is unique
  */
trait MetaInfoEnv {
  /** name of the environment (for debugging purposes)
    */
  def envName: String;


  def source: JObject;
  def gidForName(name: String, strict: Boolean = false): Option[String];
  def nameForGid(gid: String, strict: Boolean = false): Option[String];
  def metaInfoRecordForName(name: String, selfGid: Boolean = false, superGids: Boolean = false): Option[MetaInfoRecord];
  def metaInfoRecordForGid(gid: String, selfGid: Boolean = false, superGids: Boolean = false): Option[MetaInfoRecord];
}

object MetaInfoEnv {
  /** two meta infos with the same name detected
    */
  class DuplicateNameException(name: String,
    metaInfo1: MetaInfoRecord,
    metaInfo2: MetaInfoRecord
  ) extends Exception(s"DuplicateNameException, found two meta infos with the same name ($name): $metaInfo1 vs $metaInfo2") { }

  /** parsing error interpreting metainfo json
    */
  class ParseException(
    msg: String, what: Throwable = null
  ) extends Exception(msg, what) {}

}

/** Object that loads the dependencies of a NomadMetaInfo file
  */
trait DependencyResolver {
  /** Returns an environment that represents the Meta info of the given dependency
    */
  def resolveDependency(source: JObject,dep: JObject): MetaInfoEnv;

  /** Returns the parent resolver
    */
  def parentResolver: Option[DependencyResolver];

  /** returns the root reolver (the one whose parent is None)
    */
  def rootResolver: DependencyResolver = {
    parentResolver match {
      case Some(resolver) => resolver.rootResolver
      case None           => this
    }
  }
}

object DependencyResolver {

  /** thrown when a circular dependency is detected
    */
  case class CircularDepException(
    dep: JObject,
    inProgress: String
  ) extends Exception(
    s"Circular dependency encountred while resolving ${JsonUtils.prettyStr(dep)}, inProgress:$inProgress") {
  }

}

/** a MetaInfoEnv that simply stores all MetaInfoRecords and its dependencies
  */
class SimpleMetaInfoEnv(
  val envName: String,
  val source:  JObject,
  val nameToGid: Map[String, String],
  val gidToName: Map[String, String],
  val metaInfos: Map[String, MetaInfoRecord],
  val dependencies: Seq[MetaInfoEnv]) extends MetaInfoEnv {

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

  /** Returns the gid of the MetaInfoRecord with the given name
    *
    * *Not* lazy, requires the gid to be already calculated.
    * If strict is true only gids of MetaInfoRecords directly contained
    * are returned, no dependencies
    */
  def gidForName(name: String, strict: Boolean = false): Option[String] = {
    nameToGid.get(name) match {
      case Some(gid) =>
        if (strict && ! metaInfos.contains(name))
          None
        else
          Some(gid)
      case None =>
        if (strict) {
          None
        } else {
          firstFromDeps(_.gidForName(name, strict))
        }
    }
  }

  /** Returns the name of the MetaInfoRecord corresponding to the given gid
    *
    * *not* lazy, requires the gid to be already calculated
    * if strict is true only names directly contained are returned, no dependencies
    */
  def nameForGid(gid: String, strict: Boolean): Option[String] = {
    gidToName.get(gid) match {
      case Some(name) =>
        if (strict && ! metaInfos.contains(name))
          None
        else
          Some(name)
      case None =>
        if (strict) {
          None
        } else {
          firstFromDeps(_.nameForGid(gid, strict))
        }
    }
  }

  /** Returns the metaInfoRecord for the given name
    *
    * gid and super gids are set in the returned record only if requested.
    */
  def metaInfoRecordForName(name: String, selfGid: Boolean = false, superGids: Boolean = false): Option[MetaInfoRecord] = {
    metaInfos.get(name) match {
      case Some(baseVal) =>
        val gid = (
          if (selfGid)
            gidForName(name, false).getOrElse("")
          else
            ""
        )
        val sGids = (
          if (superGids) {
            if (!baseVal.superNames.isEmpty && baseVal.superGids.isEmpty)
              baseVal.superNames.map(gidForName(_, false).getOrElse(""))
            else
              baseVal.superGids
          } else {
            Seq()
          }
        )
        Some(baseVal.copy(gid = gid, superGids = sGids))
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
    nameForGid(gid, true) match {
      case Some(name) =>
        metaInfoRecordForName(name, selfGid, superGids)
      case None       => firstFromDeps(_.metaInfoRecordForGid(gid, selfGid, superGids))
    }
  }
}

object SimpleMetaInfoEnv {
  implicit val formats = DefaultFormats + new MetaInfoRecordSerializer;

  /** Calculates a Gid
    */
  def calculateGid(
    name: String,
    nameToGidCache: mutable.Map[String,String],
    metaInfos: Map[String, MetaInfoRecord],
    dependencies: Seq[MetaInfoEnv],
    context: String,
    precalculated: Boolean = false): String = {
    nameToGidCache.get(name) match {
      case Some(v) =>
        v
      case None =>
        if (precalculated) {
          throw new GidNotPrecalculatedError(name, context)
        } else {
          throw new Exception("to do")
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
    val jsonList: List[JValue] = JsonUtils.parseInputStream(stream).children // should do a more complete parsing (to dep or MetaInfoRecord) to have a better error reporting
    fromJsonList(
      name = name,
      jsonList = jsonList,
      source = source,
      dependencyResolver = dependencyResolver,
      keepExistingGidsValues = keepExistingGidsValues,
      ensureGids = ensureGids
    )
  }

  /** Initializes with a json list
    *
    * Should probabply be rewritten using strong types, not JValues, would give better error messages.
    * Use apply instead?
    */
  def fromJsonList(name: String, source: JObject, jsonList: List[JValue], dependencyResolver: DependencyResolver,
    keepExistingGidsValues: Boolean = true, ensureGids: Boolean = true): SimpleMetaInfoEnv = {
    var deps: List[MetaInfoEnv] = Nil
    val metaInfos = new mutable.HashMap[String, MetaInfoRecord]
    val nameToGid = new mutable.HashMap[String, String]
    implicit val formats = DefaultFormats + new MetaInfoRecordSerializer

    for (jsonObj <- jsonList) {
      try {
        (jsonObj \ "dependencies")  match {
          case JArray(nDeps) =>
            for (nDep <- nDeps) {
              nDep match {
                case JObject(obj) =>
                  deps = dependencyResolver.resolveDependency(source, JObject(obj)) :: deps
                case _ =>
                  throw new MetaInfoEnv.ParseException(s"expected an object as dependency, not ${JsonUtils.prettyStr(nDep)}")
              }
            }
          case JObject(obj) =>
            deps = dependencyResolver.resolveDependency(source, JObject(obj)) :: deps
          case JNothing =>
            val metaInfo = jsonObj.extract[MetaInfoRecord]
            if (metaInfos.contains(metaInfo.name))
              throw new MetaInfoEnv.DuplicateNameException(metaInfo.name, metaInfo, metaInfos(metaInfo.name))
            metaInfos += (metaInfo.name -> metaInfo)
            if (keepExistingGidsValues && !metaInfo.gid.isEmpty)
              nameToGid += (metaInfo.name -> metaInfo.gid)
          case _ =>
            throw new MetaInfoEnv.ParseException(s"unexpected value in dependencies: ${JsonUtils.prettyStr(jsonObj)}")
        }
      } catch {
        case NonFatal(e) =>
          throw new MetaInfoEnv.ParseException(s"Error processing metaInfo ${JsonUtils.prettyStr(jsonObj)}: $e", e)
      }
    }
    val metaInfosMap = metaInfos.toMap
    val dependenciesSeq = deps.toSeq
    if (ensureGids) {
      for ((name, metaInfo) <- metaInfos) {
        if (!nameToGid.contains(name))
          calculateGid(name, nameToGid, metaInfosMap, dependenciesSeq, name)
      }
    }
    new SimpleMetaInfoEnv(
      envName = name,
      source = source,
      nameToGid = nameToGid.toMap,
      gidToName = nameToGid.map{ case (name, gid) => (gid, name)}(breakOut),
      metaInfos = metaInfosMap,
      dependencies = dependenciesSeq)
  }

  /** a value that was expected to have a precalculated Gid did not have it
    */
  case class GidNotPrecalculatedError(
    name: String,
    context: String
  ) extends Exception(
    s"gid of $name was not precomputated in $context") { }

}

/** DependencyResolver that handles relativePath dependencies
  *
  * not threadsafe, it is supposed to be used by a single thread
  */
class RelativeDependencyResolver(
  val parentResolver: Option[DependencyResolver] = None,
  dependencies: mutable.Map[String, MetaInfoEnv] = null,
  namesInProgress: mutable.Set[String] = null
) extends DependencyResolver {
  implicit val formats = DefaultFormats;

  val deps = {
    if (dependencies == null)
      new mutable.HashMap[String, MetaInfoEnv]
    else
      dependencies
  }
  val inProgress = {
    if (namesInProgress == null)
      new mutable.HashSet[String]
    else
      namesInProgress
  }

  /** resolves relative paths references
    */
  def resolveDependency(source: JObject, dep: JObject): MetaInfoEnv = {
    val basePath = Paths.get((source \ "path").extract[String]).toAbsolutePath().getParent()
    val relPath = (dep \ "relativePath").extract[String]
    val dPath = basePath.resolve(relPath).toString
    if (deps.contains(dPath))
      return deps(dPath)
    if (inProgress.contains(dPath))
      throw new DependencyResolver.CircularDepException(dep, inProgress.mkString("{",", ","}"))
    inProgress += dPath
    val newEnv = SimpleMetaInfoEnv.fromFilePath(dPath, dependencyResolver = rootResolver)
    inProgress -= dPath
    deps += (dPath -> newEnv)
    return newEnv
  }
}

/*class MetaInfoDbEnv(
  val name: String,
  val dbContext: () => jooq.DSLContext,
  val lazyLoad: Boolean) {
}*/

trait MultiEnv {
  def version(name:String): MetaInfoEnv;
  def metaInfoRecordForGid(gid: String, selfGid: Boolean = false, superGids: Boolean = false): Option[MetaInfoRecord];
  def versionsForGid(gid: String): Seq[String]
}
