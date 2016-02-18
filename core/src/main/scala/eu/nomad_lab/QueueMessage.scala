package eu.nomad_lab
import org.json4s.JsonAST.{JArray, JString, JField, JObject}
import org.json4s._

object TreeType extends Enumeration {
  type TreeType = Value
  val File, Directory, Zip, Tar, Unknown = Value
}

class InvalidTreeTypeException(
                                msg: String, what: Throwable = null
                              ) extends Exception(msg, what)

/** Json serialization to and deserialization support for MetaInfoRecord
  */
class TreeTypeSerializer extends CustomSerializer[TreeType.Value](format => (
  {
    case JString(str) =>
      TreeType.withName(str)
    case JNothing =>
      TreeType.Unknown
    case v =>
      throw new InvalidTreeTypeException(s"invalid TreeType ${JsonUtils.normalizedStr(v)}")
  },
  {
    case x: TreeType.Value => JString(x.toString)
  }
  ))

object QueueMessage {

  case class TreeParserQueueMessage(
                                     treeUri: String, // URI inside the given treeFilePath; eg. nmd://R9h5Wp_FGZdsBiSo5Id6pnie_xeIH/data
                                     treeFilePath: String, //Path to the archive or the root directory eg. /nomad/nomadlab/raw_data/data/R9h/R9h5Wp_FGZdsBiSo5Id6pnie_xeIH.zip
                                     treeType: TreeType.Value = TreeType.Unknown, // type of tree we are dealing with
                                     relativeTreeFilePath: Option[String] = None, // path within the archive (optional) eg. data
                                     maxDepth: Int = -1, // In case of directory, the maxDepth to trace
                                     followSymlinks: Boolean = true // In case of directory
                                   )

  case class SingleParserQueueMessage(
                                       parserName: String, //Name of the parser to use for the file; CastepParser
                                       mainFileUri: String, //Uri of the main file; Example:  nmd://R9h5Wp_FGZdsBiSo5Id6pnie_xeIH/data/examples/foo/Si2.castep
                                       relativeFilePath: String, // file path, from the tree root. Example data/examples/foo/Si2.castep
                                       treeFilePath: String, // Same as the treeFilePath in TreeParserQueueMessage; eg. /nomad/nomadlab/raw_data/data/R9h/R9h5Wp_FGZdsBiSo5Id6pnie_xeIH.zip
                                       treeType: TreeType.Value = TreeType.Unknown // type of root tree we are dealing with
                                     )

  case class ToBeNormalizedQueueMessage(
                                     parserInfo: JValue, // info on the parser used i.e. {"name":"CastepParser","version":"1.0"}
                                     mainFileUri: String, //Uri of the main file; Example:  nmd://R9h5Wp_FGZdsBiSo5Id6pnie_xeIH/data/examples/foo/Si2.castep
                                     parsedFileUri: String, // This is build as sha of mainFileUri, prepended with P, i.e. nmd://PutioKaDl4tgPd4FnrdxPscSGKAgK
                                     parsedFilePath: String // Complete file path, to the parsed file /nomad/nomadlab/work/parsed/<parserId>/Put/PutioKaDl4tgPd4FnrdxPscSGKAgK.nc
                                   )

  case class NormalizedQueueMessage(
                                     normalizedFileUri: String, // This is build as sha of archive, changing R into N, i.e. nmd://N9h5Wp_FGZdsBiSo5Id6pnie_xeIH
                                     normalizedFilePath: String // Complete file path, to the normalized file /nomad/nomadlab/normalized/<parserId>/N9h/N9h5Wp_FGZdsBiSo5Id6pnie_xeIH.nc
                                   )


}
