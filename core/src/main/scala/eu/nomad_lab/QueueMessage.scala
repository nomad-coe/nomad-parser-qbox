package eu.nomad_lab

object QueueMessage {
  case class TreeParserQueueMessage(
                                     treeUri: String, // URI inside the given treeFilePath; eg. examples.zip
                                     treeFilePath: String, //Path without URI for the resource. eg. nomad/raw_data/data/
                                     maxDepth: Int = -1, // In case of directory, the maxDepth to trace
                                     followSymlinks: Boolean = true // In case of directory
                                   )

  case class SingleParserQueueMessage(
                                       parserName: String, //Name of the parser to use for the file; CastepParser
                                       fileUri: String, //Uri of the main file; Example: examples/foo/Si2.castep
                                       filePath: Option[String], // Complete file path, includes the path inside the compressed file as well as the name & path of the compressed file. Example /nomad/raw_data/data/examples.zip/examples/foo/Si2.castep
                                       treeFilePath: Option[String], // Same as the treeFilePath in TreeParserQueueMessage; eg. /nomad/raw_data/data/
                                       mimeType: String = "application/zip"
                                     )

  case class NormalizerQueueMessage(
                                     parserName: String, //Name of the parser to use for the file; CastepParser
                                     parsedFileUri: String, //Uri of the final parsed data produced by the parser
                                     parsedFilePath: Option[String] // Path without URI for the file
                                   )

}
