package eu.nomad_lab.meta;
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import scala.collection.mutable
import scala.collection.breakOut
import org.{json4s => jn}
import java.nio.file.Path
import java.nio.file.Paths

object KnownMetaInfoEnvs extends MetaInfoCollection {
  val lastCommon = {
    val classLoader: ClassLoader = getClass().getClassLoader();
    val filePath = classLoader.getResource("nomad_meta_info/common.nomadmetainfo.json").getFile()
    val resolver = new RelativeDependencyResolver
    val mainEnv = SimpleMetaInfoEnv.fromFilePath(filePath, resolver)
    new SimpleMetaInfoEnv(
      name = "lastCommon",
      description = "latest version of the common values, unlike all others this one is symbolic and will change in time",
      source = jn.JObject( jn.JField("path", jn.JString(Paths.get(filePath).getParent().toString())) ),
      nameToGid = Map[String, String](),
      gidToName = Map[String, String](),
      metaInfosMap = Map[String, MetaInfoRecord](),
      dependencies = Seq(mainEnv),
      kind = MetaInfoEnv.Kind.Version)
  }

  val lastAll = {
    val classLoader: ClassLoader = getClass().getClassLoader();
    val filePath = classLoader.getResource("nomad_meta_info/all.nomadmetainfo.json").getFile()
    val resolver = new RelativeDependencyResolver
    val mainEnv = SimpleMetaInfoEnv.fromFilePath(filePath, resolver)
    new SimpleMetaInfoEnv(
      name = "lastAll",
      description = "latest version of all the metaInfo (including code specific), unlike all others this one is symbolic and will change in time",
      source = jn.JObject( jn.JField("path", jn.JString(Paths.get(filePath).getParent().toString())) ),
      nameToGid = Map[String, String](),
      gidToName = Map[String, String](),
      metaInfosMap = Map[String, MetaInfoRecord](),
      dependencies = Seq(mainEnv),
      kind = MetaInfoEnv.Kind.Version)
  }

  val envs: Map[String, MetaInfoEnv] = Map(
    "lastCommon" -> lastCommon,
    "lastAll"    -> lastAll,
    "last"       -> new SimpleMetaInfoEnv(
      name = "last",
      description = "legacy latest version of the common values, unlike all others this one is symbolic and will change in time",
      source = lastCommon.source,
      nameToGid = Map[String, String](),
      gidToName = Map[String, String](),
      metaInfosMap = Map[String, MetaInfoRecord](),
      dependencies = lastCommon.dependencies,
      kind = MetaInfoEnv.Kind.Version))

  /** returns all versions defined (might contain duplicates!)
    */
  override def allEnvs: Iterator[MetaInfoEnv] = envs.valuesIterator

  /** returns the versions with the given name
    */
  override def versionsWithName(name:String): Iterator[MetaInfoEnv] = {
    envs.get(name) match {
      case Some(env) => Iterator(env)
      case None => Iterator.empty
    }
  }

  /** returns the versions that contain that gid
    *
    * If recursive is true, inclusion through a dependency is also
    * considered.
    */
  override def versionsForGid(gid: String, recursive: Boolean = false): Iterator[String] = {
    envs.flatMap { case (vName, env) =>
      if (env.allGids.contains(gid))
        Some(vName)
      else
        None
    }.toIterator
  }

  /** All gids of the meta infos in this collection
    *
    * might contain duplicates
    */
  override def allGids: Iterator[String] = {
    envs.foldLeft(Iterator.empty: Iterator[String])( _ ++ _._2.allGids)
  }

  /** returns the MetaInfoRecord corresponding to the given gid
    *
    * gids and superGids are added only if requested
    */
  override def metaInfoRecordForGid(gid: String, selfGid: Boolean = false, superGids: Boolean = false): Option[MetaInfoRecord] = {
    for (env <- allEnvs) {
      metaInfoRecordForGid(gid, selfGid, superGids) match {
        case Some(value) =>
          return Some(value)
        case None =>
          ()
      }
    }
    None
  }

}
