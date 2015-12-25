package eu.nomad_lab.meta;
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import scala.collection.mutable
import scala.collection.breakOut
import org.{json4s => jn}
import java.nio.file.Path
import java.nio.file.Paths
import com.typesafe.scalalogging.StrictLogging

object KnownMetaInfoEnvs extends MetaInfoCollection with StrictLogging {
  val envs: mutable.Map[String, MetaInfoEnv] = mutable.Map()

  /** loads a meta info env from the resouces
    */
  def loadMetaEnv(metaPath: String, versionName: String = "", description: String = ""): MetaInfoEnv = {
    try {
      val classLoader: ClassLoader = getClass().getClassLoader();
      val filePath = Paths.get(classLoader.getResource(metaPath).getFile())
      val baseName = filePath.getName(filePath.getNameCount()-1).toString()
      val vName = if (!versionName.isEmpty)
        versionName
      else if (baseName.endsWith(".nomadmetainfo.json"))
        baseName.dropRight(19)
      else if (baseName.endsWith(".json"))
        baseName.dropRight(5)
      else
        baseName
      val resolver = new RelativeDependencyResolver
      val mainEnv = SimpleMetaInfoEnv.fromFilePath(filePath.toString(), resolver)
      val desc = if (!description.isEmpty)
        description
      else
        mainEnv.description
      val res = new SimpleMetaInfoEnv(
        name = vName,
        description = desc,
        source = jn.JObject( jn.JField("path", jn.JString(filePath.getParent().toString())) ),
        nameToGid = Map[String, String](),
        gidToName = Map[String, String](),
        metaInfosMap = Map[String, MetaInfoRecord](),
        dependencies = Seq(mainEnv),
        kind = MetaInfoEnv.Kind.Version)
      envs += (vName -> res)
      return res
    } catch {
      case e: Exception =>
        logger.warn(s"Exception $e while loading meta env at ${metaPath}")
        throw new Exception(s"Exception $e while loading meta env at ${metaPath}", e)
    }
  }

  val common = {
    loadMetaEnv("nomad_meta_info/common.nomadmetainfo.json",
      versionName = "common",
      description = "latest version of the common values, unlike all others this one is symbolic and will change in time")
  }
  val all = {
    loadMetaEnv("nomad_meta_info/all.nomadmetainfo.json",
      versionName = "all",
      description = "latest version of all the metaInfo (including code specific)")
  }
  val last = {
    loadMetaEnv("nomad_meta_info/main.nomadmetainfo.json",
      versionName = "last",
      description = "latest version of the common values and meta types")
  }
  val fhiAims = {
    loadMetaEnv("nomad_meta_info/fhi_aims.nomadmetainfo.json",
    description = "FHI aims meta info and its dependencies")
  }
  val cp2k = {
    loadMetaEnv("nomad_meta_info/cp2k.nomadmetainfo.json",
    description = "CP2K meta info and its dependencies")
  }
  val exciting = {
    loadMetaEnv("nomad_meta_info/exciting.nomadmetainfo.json",
    description = "exciting meta info and its dependencies")
  }
  val gaussian = {
    loadMetaEnv("nomad_meta_info/gaussian.nomadmetainfo.json",
    description = "Gaussian meta info and its dependencies")
  }
  val sampleParser = {
    loadMetaEnv("nomad_meta_info/sample_parser.nomadmetainfo.json",
    description = "sample_parser and its dependencies")
  }
  val lammps = {
    loadMetaEnv("nomad_meta_info/lammps.nomadmetainfo.json",
    description = "LAMMPS meta info and its dependencies")
  }
  val octopus = {
    loadMetaEnv("nomad_meta_info/octopus.nomadmetainfo.json",
    description = "octopus meta info and its dependencies")
  }
  val quantumEspresso = {
    loadMetaEnv("nomad_meta_info/quantum_espresso.nomadmetainfo.json",
    description = "Quantum Espresso meta info and its dependencies")
  }
  val turbomole = {
    loadMetaEnv("nomad_meta_info/turbomole.nomadmetainfo.json",
    description = "TURBOMOLE meta info and its dependencies")
  }
  val gpaw = {
    loadMetaEnv("nomad_meta_info/gpaw.nomadmetainfo.json",
    description = "GPAW meta info and its dependencies")
  }
  val castep = {
    loadMetaEnv("nomad_meta_info/castep.nomadmetainfo.json",
    description = "CASTEP meta info and its dependencies")
  }

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
