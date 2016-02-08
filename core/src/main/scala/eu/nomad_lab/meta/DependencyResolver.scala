package eu.nomad_lab.meta
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import org.json4s.DefaultFormats
import java.nio.file.Path
import java.nio.file.Paths
import scala.collection.mutable
import eu.nomad_lab.JsonUtils

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
  class CircularDepException(
    source: JObject,
    dep: JObject,
    inProgress: String
  ) extends Exception(
    s"Circular dependency encountred while resolving ${JsonUtils.prettyStr(dep)} in ${JsonUtils.prettyStr(source)}, inProgress:$inProgress") {
  }

  /** thrown when the given dependency is found but not expected
    */
  class UnexpectedDepException(
    source: JObject,
    dep: JObject
  ) extends Exception(
    s"Unexprected dependency ${JsonUtils.prettyStr(dep)} in ${JsonUtils.prettyStr(source)}") {
  }

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
    val basePath: Path = Paths.get((source \ "path").extract[String]).toAbsolutePath().getParent()
    val relPath = (dep \ "relativePath").extract[String]
    val dPath = basePath.resolve(relPath).toString

    if (deps.contains(dPath))
      return deps(dPath)
    if (inProgress.contains(dPath))
      throw new DependencyResolver.CircularDepException(
        source, dep, inProgress.mkString("{",", ","}"))
    inProgress += dPath
    val newEnv = SimpleMetaInfoEnv.fromFilePath(dPath, dependencyResolver = rootResolver)
    inProgress -= dPath
    deps += (dPath -> newEnv)
    return newEnv
  }
}

/** DependencyResolver that handles relativePath dependencies
  *
  * not threadsafe, it is supposed to be used by a single thread
  */
class ResourceDependencyResolver(
                                  val classResolver: ClassLoader,
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
    val basePath: Path = Paths.get((source \ "path").extract[String]).getParent()
    val relPath = (dep \ "relativePath").extract[String]
    val dPath = basePath.resolve(relPath).toString

    if (deps.contains(dPath))
      return deps(dPath)
    if (inProgress.contains(dPath))
      throw new DependencyResolver.CircularDepException(
        source, dep, inProgress.mkString("{",", ","}"))
    inProgress += dPath
    val newEnv = SimpleMetaInfoEnv.fromInputStream(classResolver.getResourceAsStream(dPath), dPath,
        JObject(
          ("path" -> JString(dPath)) ::
          ("base" -> source) :: Nil),
      rootResolver)
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
      throw new DependencyResolver.UnexpectedDepException(source, dep)
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
