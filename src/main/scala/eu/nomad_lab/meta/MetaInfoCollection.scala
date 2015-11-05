package eu.nomad_lab.meta
import scala.collection.mutable

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
