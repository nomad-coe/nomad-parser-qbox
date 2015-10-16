package eu.nomad_lab
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import org.json4s.{DefaultFormats, CustomSerializer}
//import org.jooq;
import java.io.{FileInputStream, InputStream}
import java.nio.file.{Path, Paths}
import scala.collection.mutable
import scala.collection.breakOut
import scala.util.control.NonFatal
import com.typesafe.scalalogging.StrictLogging

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
  val shape:  Option[Seq[Either[Int,String]]] = None,
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
    val jShape = shape match {
      case None => JNothing
      case Some(s) =>
        val listShape: List[JValue] = s.map {
          case Left(i) => JInt(i)
          case Right(s) => JString(s)
        }(breakOut)
        JArray(listShape)
    }
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
        ("shape",jShape)
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
    * D: a json dictionary (currently not very efficient)
    *
    * Should probably be migrated to an Enumaration.
    */
  final val dtypes = Seq("f", "i", "f32", "i32", "u32", "f64", "i64", "u64", "b", "c", "B", "C", "D")
}

/** Json serialization to and deserialization support for MetaInfoRecord
  */
class MetaInfoRecordSerializer extends CustomSerializer[MetaInfoRecord](format => (
         {
           case JObject(obj) => {
             implicit val formats = format;
             var name: String = "";
             var gid: String = "";
             var kindStr: String = "DocumentContentType";
             var description: String = "";
             var superNames: Seq[String] = Seq();
             var superGids: Seq[String] = Seq();
             var units: Option[String] = None;
             var dtypeStr: Option[String] = None;
             var repeats: Option[Boolean] = None;
             var shape: Option[Seq[Either[Int,String]]] = None;
             var otherKeys: List[JField] = Nil;
             obj foreach {
               case JField("name", value) =>
                 value match {
                   case JString(s)       => name = s
                   case JNothing | JNull => ()
                   case _                => throw new JsonUtils.InvalidValueError(
                     "name", "NomadMetaInfo", JsonUtils.prettyStr(value), "a string")
                 }
               case JField("gid", value) =>
                 value match {
                   case JString(s)       => gid = s
                   case JNothing | JNull => ()
                   case _                => throw new JsonUtils.InvalidValueError(
                     "gid", "NomadMetaInfo", JsonUtils.prettyStr(value), "a string")
                 }
               case JField("kindStr", value) =>
                 value match {
                   case JString(s)       => kindStr = s
                   case JNothing | JNull => ()
                   case _                => throw new JsonUtils.InvalidValueError(
                     "kindString", "NomadMetaInfo", JsonUtils.prettyStr(value), MetaInfoRecord.dtypes.foldLeft("one of the following strings:"){ _ + " " + _ } )
                 }
               case JField("description", value) =>
                 value match {
                   case JString(s)       => description = s
                   case JArray(arr)      =>
                     val sb = new StringBuilder()
                     arr.foreach{
                       case JString(s) => sb ++= (s)
                       case JNothing   => ()
                       case _          => throw new JsonUtils.InvalidValueError(
                     "description", "NomadMetaInfo", JsonUtils.prettyStr(value), "either a string or an array of strings")
                     }
                     description = sb.toString
                   case JNothing | JNull => ()
                   case _                => throw new JsonUtils.InvalidValueError(
                     "description", "NomadMetaInfo", JsonUtils.prettyStr(value), "either a string or an array of strings")
                 }
               case JField("superNames", value) =>
                 if (!value.toOption.isEmpty)
                   superNames = value.extract[Seq[String]]
               case JField("superGids", value) =>
                 if (!value.toOption.isEmpty)
                   superGids = value.extract[Seq[String]]
               case JField("units", value) =>
                 if (!value.toSome.isEmpty)
                   units = value.extract[Option[String]]
               case JField("dtypeStr", value) =>
                 if (!value.toSome.isEmpty)
                   dtypeStr = value.extract[Option[String]]
               case JField("repeats", value) =>
                 if (!value.toSome.isEmpty)
                   repeats = value.extract[Option[Boolean]]
               case JField("shape", value) =>
                 shape = value.extract[Option[Seq[Either[Int,String]]]]
               case JField(key, value) =>
                 if (!value.toSome.isEmpty)
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

/** Interface to a collection of nomad meta info records
  */
trait MetaInfoCollection {

  /** returns all versions defined (might contain duplicates!)
    */
  def allEnvs: Iterator[MetaInfoEnv]

  /** returns all versions just once
    */
  def allUniqueEnvs(filter: MetaInfoEnv => Boolean): Iterator[MetaInfoEnv] = {
    val seen = mutable.Set[MetaInfoEnv]()

    allEnvs.filter{ el =>
      if (!filter(el) || seen(el)) {
        false
      } else {
        seen += el
        true
      }
    }
  }

  /** returns the versions with the given name
    */
  def versionsWithName(name:String): Iterator[MetaInfoEnv] = {
    allUniqueEnvs{ env: MetaInfoEnv => env.kind == MetaInfoEnv.Kind.Version && env.name == name }
  }

  /** returns the versions that contain that gid
    *
    * If recursive is true, inclusion through a dependency is also
    * considered.
    */
  def versionsForGid(gid: String, recursive: Boolean = false): Iterator[String]

  /** All gids of the meta infos in this collection
    *
    * might contain duplicates
    */
  def allGids: Iterator[String]

  /** returns the MetaInfoRecord corresponding to the given gid
    *
    * gids and superGids are added only if requested
    */
  def metaInfoRecordForGid(gid: String, selfGid: Boolean = false, superGids: Boolean = false): Option[MetaInfoRecord];
}

/** Interface to a set of MetaInfoRecords in which MetaInfoRecord.name is
  * unique
  *
  * An environment in which names have a unique meaning is required to
  * calculate gids if the dependencies are expressed only through superNames.
  * This can also be seen as a "Version" of the meta infos.
  */
trait MetaInfoEnv extends MetaInfoCollection {
  /** Name of the version
    */
  def name: String;

  /** A description of this environment
    */
  def description: String;

  /** Info describing from where this version was created
    *
    * example: path of the file read
    */
  def source: JObject;

  def kind: MetaInfoEnv.Kind.Kind;

  /** The names of the meta info contained directly in this environment,
    * no dependencies
    */
  def names: Iterator[String]

  /** The meta infos contained in this environment
    */
  def metaInfos(selfGid: Boolean, superGids: Boolean): Iterator[MetaInfoRecord]

  /** gids of the meta infos contained in this environment (no dependencies)
    */
  def gids: Seq[String]

  /** All gids of the meta infos in this collection
    *
    * might contain duplicates
    */
  override def allGids: Iterator[String] = {
    allUniqueEnvs(_ => true).foldLeft(gids.toIterator)( _ ++ _.gids.toIterator)
  }


  /** returns the versions with the given name
    */
  override def versionsWithName(name:String): Iterator[MetaInfoEnv] = {
    val subVers = allUniqueEnvs{ env: MetaInfoEnv => env.kind == MetaInfoEnv.Kind.Version && env.name == name }
    if (kind == MetaInfoEnv.Kind.Version && this.name == name)
      Iterator(this) ++ subVers
    else
      subVers
  }

  /** All names of the meta infos in this environment (including dependencies)
    *
    * Might contain duplicates
    */
  def allNames: Iterator[String] = {
    allUniqueEnvs(_ => true).foldLeft(names.toIterator)( _ ++ _.names.toIterator)
  }

  /** Maps names to gids
    *
    * If recursive is false only direct descendents are mapped.
    */
  def gidForName(name: String, recursive: Boolean = true): Option[String]

  /** Maps gids to names
    *
    * If recursive is false only direct descendents are mapped.
    */
  def nameForGid(gid: String, recursive: Boolean = true): Option[String]

  /** returns the MetaInfoRecord corresponding to the given name
    *
    * gids and superGids are added only if requested
    */
  def metaInfoRecordForName(name: String, selfGid: Boolean = false, superGids: Boolean = false): Option[MetaInfoRecord]

  /** Converts the environment to json
    *
    * metaInfoWriter should write out the MetaInfo it receives (if necessary), and return a
    * JObject that can be written out as dependency to load that MetaInfoEnv.
    */
  def toJValue(metaInfoWriter: MetaInfoEnv => JValue, selfGid: Boolean = false,
    superGids: Boolean = false, flat: Boolean = true): JObject = {
    val deps = JArray(if (!flat) {
      allEnvs.map(metaInfoWriter).toList
    } else  {
      Nil
    })

    val mInfos = JArray(if (!flat) {
      names.toSeq.sorted.flatMap{ name: String =>
        metaInfoRecordForName(name, selfGid = selfGid, superGids = superGids) match {
          case Some(r) => Some(r.toJValue())
          case None => None
        }
      }(breakOut): List[JValue]
    } else {
      allNames.toSet.toSeq.sorted.flatMap{ name: String =>
        metaInfoRecordForName(name, selfGid = selfGid, superGids = superGids) match {
          case Some(r) => Some(r.toJValue())
          case None => None
        }
      }(breakOut): List[JValue]
    })

    JObject(JField("type",  JString("nomad_meta_info_1_0")) ::
      JField("name", JString(name)) ::
      JField("description", JString(description)) ::
      JField("dependencies", deps) ::
      JField("metaInfos", mInfos) :: Nil)
  }

  /** Iterator that starting with base iterates on all its ancestors
    *
    * call next once if you want to skip base itself.
    *
    * nest in method and use context implicitly?
    */
  class SubIter(context: MetaInfoEnv, base: MetaInfoRecord, selfGid: Boolean, superGids: Boolean) extends Iterator[MetaInfoRecord] {
    val known = mutable.Set[String](base.name)
    val toDo = mutable.ListBuffer(base.name)

    override def hasNext: Boolean = !toDo.isEmpty

    override def next(): MetaInfoRecord = {
      val now = toDo.head
      toDo.trimStart(1)
      val nowR = context.metaInfoRecordForName(now, selfGid = selfGid, superGids = superGids)
      if (nowR.isEmpty)
        throw new MetaInfoEnv.DependsOnUnknownNameException(context.name, known.toString, now)
      nowR.get.superNames.foreach { superName: String =>
        if (!known(superName)) {
          toDo.append(superName)
          known += superName
        }
      }
      nowR.get
    }
  }

  /** Iterates on the given name and all its ancestors
    */
  def metaInfoRecordForNameWithAllSuper(name: String, selfGid: Boolean = false, superGids: Boolean = false): Iterator[MetaInfoRecord] = {
    this.metaInfoRecordForName(name, selfGid = selfGid, superGids = superGids) match {
      case Some(r) => new SubIter(this, r, selfGid, superGids)
      case None    => Iterator()
    }
  }

  /** ancestors by type, subdivided in roots, and children
    *
    * The roots of type X are the ancestors of type X that cannot be reached starting
    * from others ancestors of type X going though ancestors of all types.
    * children are those than can be reached
    */
  def firstAncestorsByType(name: String): Map[String,Tuple2[Set[String],Set[String]]] = {
    val mInfo = metaInfoRecordForNameWithAllSuper(name, true, true).toArray
    val nameMap: Map[String, Int] = mInfo.zipWithIndex.map{ case (mInfo, i) =>
      mInfo.name -> i }(breakOut)
    val edges: Map[Int, Seq[Int]] = mInfo.zipWithIndex.map{ case (mInfo, i) =>
      i -> mInfo.superNames.map(nameMap(_)).toSeq
    }(breakOut)
    val typeGroups = (1.until(mInfo.length)).groupBy( i => mInfo(i).kindStr )
    val childsByType = mutable.Map[String, mutable.Set[Int]]()
    val toDo  = mutable.Set[Int](1.until(mInfo.length) : _*)

    while (!toDo.isEmpty) {
      val now = toDo.head
      val kindNow = mInfo(now).kindStr
      toDo -= now
      val toDo2 = mutable.Set(edges(now): _*)
      val known2 = mutable.Set(edges(now): _*)
      while (!toDo2.isEmpty) {
        val now2 = toDo2.head
        toDo2 -= now2
        if (mInfo(now2).kindStr == kindNow) {
          if (childsByType.contains(kindNow))
            childsByType(kindNow) += now2
          else
            childsByType += (kindNow -> mutable.Set(now2))
          toDo -= now2
        }
        for (el <- edges(now2)) {
          if (!known2(el)) {
            toDo2 += el
            known2 += el
          }
        }
      }
    }

    childsByType.map{ case (kindStr, childs) =>
      val allForKind = typeGroups(kindStr).toSet
      val rootNames: Set[String] = (allForKind -- childs).map(mInfo(_).name)(breakOut)
      val childNames: Set[String] = childs.map(mInfo(_).name)(breakOut)
      kindStr -> (rootNames -> childNames)
    }(breakOut): Map[String,Tuple2[Set[String],Set[String]]]
  }

  /** Returns the names of the direct children of the metaInfo with the given name
    *
    * Direct children are those that have name in superNames.
    * Only meta info in the environment are returned (no dependencies)
    */
  def directChildrenOf(name: String): Iterator[String] = {
    metaInfos(false,false).flatMap{ metaInfo: MetaInfoRecord =>
      if (metaInfo.superNames.contains(name))
        Some(metaInfo.name)
      else
        None
    }.toIterator
  }

  /** Returns all the names of the direct childrens of the metaInfo with the given name
    * (with dependencies)
    *
    * Direct children are those that have name in superNames.
    */
  def allDirectChildrenOf(name: String): Iterator[String] = {
    allUniqueEnvs(_ => true).foldLeft(directChildrenOf(name))( _ ++ _.directChildrenOf(name))
  }
}

object MetaInfoEnv {
  /** Enum for various kinds of environment
    */
  object Kind extends Enumeration {
    type Kind = Value
    /** The environment represents a file
      */
    val File = Value

    /** The environment represents a version (spanning possibly multiple files)
      */
    val Version = Value

    /** A pseudo environment (for example one corresponding to a gid)
      */
    val Pseudo = Value
  }

  /** two meta infos with the same name detected
    */
  case class DuplicateNameException(name: String,
    metaInfo1: MetaInfoRecord,
    metaInfo2: MetaInfoRecord
  ) extends Exception(s"DuplicateNameException, found two meta infos with the same name ($name): $metaInfo1 vs $metaInfo2") { }

  /** parsing error interpreting metainfo json
    */
  class ParseException(
    msg: String, what: Throwable = null
  ) extends Exception(msg, what) {}

  /** a meta info depends on an unknown name
    */
  case class DependsOnUnknownNameException(
    envName: String,
    name: String,
    unknownName: String
  ) extends Exception(
    s"Meta info '$name' depends on unknown meta info '$unknownName' in '$envName'") {}

  /** a meta info has a circular dependency
    */
  case class MetaInfoCircularDepException(
    envName: String,
    name: String,
    nameInCicle: String,
    inProgress: Iterable[String]
  ) extends Exception(
    s"found loop to '$nameInCicle' evaluating '$name' in '$envName', currently in progress: ${inProgress.mkString("[",", ","]")}") { }

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
    source: JObject,
    dep: JObject,
    inProgress: String
  ) extends Exception(
    s"Circular dependency encountred while resolving ${JsonUtils.prettyStr(dep)} in ${JsonUtils.prettyStr(source)}, inProgress:$inProgress") {
  }

  /** thrown when the given dependency is found but not expected
    */
  case class UnexpectedDepException(
    source: JObject,
    dep: JObject
  ) extends Exception(
    s"Unexprected dependency ${JsonUtils.prettyStr(dep)} in ${JsonUtils.prettyStr(source)}") {
  }

}

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
      throw DependencyResolver.CircularDepException(
        source, dep, inProgress.mkString("{",", ","}"))
    inProgress += dPath
    val newEnv = SimpleMetaInfoEnv.fromFilePath(dPath, dependencyResolver = rootResolver)
    inProgress -= dPath
    deps += (dPath -> newEnv)
    return newEnv
  }
}

/** dummy DependencyResolver that resolves no dependency
  */
class NoDependencyResolver(
  val parentResolver: Option[DependencyResolver] = None,
  val throwOnDep: Boolean = true
) extends DependencyResolver {
  /** throws or returns a dummy env depending on throwOnDep
    */
  def resolveDependency(source: JObject, dep: JObject): MetaInfoEnv = {
    if (throwOnDep)
      throw DependencyResolver.UnexpectedDepException(source, dep)
    return new SimpleMetaInfoEnv("dummyEnv",
      description = "dummy environment replacing a dependency",
      source =  JsonUtils.mergeObjects(source,dep),
      nameToGid = Map[String, String](),
      gidToName = Map[String, String](),
      metaInfosMap = Map[String, MetaInfoRecord](),
      dependencies = Seq(),
      kind = MetaInfoEnv.Kind.Pseudo)
  }
}

/*class MetaInfoDbEnv(
  val name: String,
  val dbContext: () => jooq.DSLContext,
  val lazyLoad: Boolean) {
}*/
