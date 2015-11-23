package eu.nomad_lab.meta
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import scala.collection.mutable
import scala.collection.breakOut

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
      allUniqueEnvs().map(metaInfoWriter).toList
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

  /** writes a dot format description of the graph
    */
  def writeDot(s: java.io.Writer): Unit = {
    s.write("""strict digraph meta_info_env {
  rankdir=RL;
""")
    allNames.foreach{ name: String =>
      metaInfoRecordForName(name, false, false) match {
        case Some(m) =>
          val attributes = m.kindStr match {
            case "type_document_content" => "[shape=box]"
            case "type_unknown" => "[color=green]"
            case "type_unknown_meta" => "[color=green]"
            case "type_document" => "[color=grey]"
            case "type_meta" => "[color=blue]"
            case "type_abstract_document_content" => ""
            case "type_section" => "[color=red]"
            case "type_connection" => "[color=orange]"
            case _ => "[color=pink]"
          }
          if (!attributes.isEmpty)
            s.write(s"  ${m.name} $attributes;\n")
          for (superN <- m.superNames)
            s.write(s"  ${m.name} -> $superN;\n")
        case None =>
          throw new Exception(s"Could not ger meta info with name $name")
      }
    }
    s.write("}\n")
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
  class DuplicateNameException(name: String,
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
  class DependsOnUnknownNameException(
    envName: String,
    name: String,
    unknownName: String
  ) extends Exception(
    s"Meta info '$name' depends on unknown meta info '$unknownName' in '$envName'") {}

  /** a meta info has a circular dependency
    */
  class MetaInfoCircularDepException(
    envName: String,
    name: String,
    nameInCicle: String,
    inProgress: Iterable[String]
  ) extends Exception(
    s"found loop to '$nameInCicle' evaluating '$name' in '$envName', currently in progress: ${inProgress.mkString("[",", ","]")}") { }

}
