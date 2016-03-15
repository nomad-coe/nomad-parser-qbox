package eu.nomad_lab

import eu.nomad_lab.parsers.ParseResult.ParseResult
import org.json4s.JsonAST.{JArray, JString, JField, JObject}
import org.json4s._

/** Enum of possible tree/archive type
  */
object TreeType extends Enumeration {
  type TreeType = Value
  val File, Directory, Zip, Tar, Unknown = Value
}

class InvalidTreeTypeException(
                                msg: String, what: Throwable = null
                              ) extends Exception(msg, what)

/** Json serialization and deserialization support for MetaInfoRecord
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

  /** Request sent to the tree parser to find the appropriate parser for the given tree/archive
    */
  case class TreeParserRequest(
                                     treeUri: String, // URI inside the given treeFilePath; eg. nmd://R9h5Wp_FGZdsBiSo5Id6pnie_xeIH/data
                                     treeFilePath: String, //Path to the archive or the root directory eg. /nomad/nomadlab/raw_data/data/R9h/R9h5Wp_FGZdsBiSo5Id6pnie_xeIH.zip
                                     treeType: TreeType.Value = TreeType.Unknown, // type of tree we are dealing with
                                     relativeTreeFilePath: Option[String] = None, // path within the archive (optional) eg. data
                                     maxDepth: Int = -1, // In case of directory, the maxDepth to trace
                                     followSymlinks: Boolean = true // In case of directory
                                   )

  /** Request send to the calculation parser to uncompress the tree/archive and initialize the parser
    */
  case class CalculationParserRequest(
                                       parserName: String, //Name of the parser to use for the file; CastepParser
                                       mainFileUri: String, //Uri of the main file; Example:  nmd://R9h5Wp_FGZdsBiSo5Id6pnie_xeIH/data/examples/foo/Si2.castep
                                       relativeFilePath: String, // file path, from the tree root. Example data/examples/foo/Si2.castep
                                       treeFilePath: String, // Same as the treeFilePath in TreeParserQueueMessage; eg. /nomad/nomadlab/raw_data/data/R9h/R9h5Wp_FGZdsBiSo5Id6pnie_xeIH.zip
                                       treeType: TreeType.Value = TreeType.Unknown, // type of root tree we are dealing with
                                       overwrite: Boolean = false // Overwrite an existing file; eg. In case of failure of previous run
                                     )

  /** Result of the calculation parser.
    */
  case class CalculationParserResult(
                                         parseResult:ParseResult,
                                         parserInfo: JValue, // info on the parser used i.e. {"name":"CastepParser","version":"1.0"}
                                         parsedFileUri: Option[String], // This is build as sha of mainFileUri, prepended with P, i.e. nmd://PutioKaDl4tgPd4FnrdxPscSGKAgK
                                         parsedFilePath: Option[String],  // Complete file path, to the parsed file /nomad/nomadlab/work/parsed/<parserId>/Put/PutioKaDl4tgPd4FnrdxPscSGKAgK.nc
                                         didExist: Boolean, // Did the parsed file did exist
                                         created: Boolean, // Was a new parsed file created
                                         errorMessage:Option[String] = None,
                                         parseRequest: CalculationParserRequest//Uri of the main file; Example:  nmd://R9h5Wp_FGZdsBiSo5Id6pnie_xeIH/data/examples/foo/Si2.castep
                                         )

  /** Result of the calculation parser to be normalized.
    */
  case class ToBeNormalizedQueueMessage(
                                     parserInfo: JValue, // info on the parser used i.e. {"name":"CastepParser","version":"1.0"}
                                     mainFileUri: String, //Uri of the main file; Example:  nmd://R9h5Wp_FGZdsBiSo5Id6pnie_xeIH/data/examples/foo/Si2.castep
                                     parsedFileUri: String, // This is build as sha of mainFileUri, prepended with P, i.e. nmd://PutioKaDl4tgPd4FnrdxPscSGKAgK
                                     parsedFilePath: String // Complete file path, to the parsed file /nomad/nomadlab/work/parsed/<parserId>/Put/PutioKaDl4tgPd4FnrdxPscSGKAgK.nc
                                   )

  /** The final normalized result.
    */
  case class NormalizedResult(
                                     normalizedFileUri: String, // This is build as sha of archive, changing R into N, i.e. nmd://N9h5Wp_FGZdsBiSo5Id6pnie_xeIH
                                     normalizedFilePath: String // Complete file path, to the normalized file /nomad/nomadlab/normalized/<parserId>/N9h/N9h5Wp_FGZdsBiSo5Id6pnie_xeIH.nc
                                   )
}
