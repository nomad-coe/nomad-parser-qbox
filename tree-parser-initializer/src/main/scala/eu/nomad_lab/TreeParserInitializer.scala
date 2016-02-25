package eu.nomad_lab

import java.nio.file.Paths

import com.typesafe.config.{ConfigFactory, Config}
import com.typesafe.scalalogging.StrictLogging
import eu.nomad_lab.QueueMessage.TreeParserQueueMessage
import eu.nomad_lab.parsing_queue.TreeParser
import org.json4s.JsonAST.JValue

import com.rabbitmq.client._



object TreeParserInitilaizer extends StrictLogging {

  /** The settings required to get the read and write queue
    */
  class Settings (config: Config) {
    // validate vs. reference.conf
    config.checkValid(ConfigFactory.defaultReference(), "simple-lib")
    val rabbitMQHost = config.getString("nomad_lab.parser_worker_rabbitmq.rabbitMQHost")
    val writeQueue = config.getString("nomad_lab.parser_worker_rabbitmq.treeParserQueue")

    def toJValue: JValue = {
      import org.json4s.JsonDSL._;
      ( ("rabbitMQHost" -> rabbitMQHost) ~
        ("writeQueue" -> writeQueue))
    }
  }

  val settings = new Settings(ConfigFactory.load())
  val parserCollection = parsers.AllParsers.defaultParserCollection
  val treeParser = new TreeParser(
    parserCollection = parserCollection
  )

  def main(args: Array[String]) = {
    writeToQueue(args)
  }

/** Find the parsable files and parsers. Write this information for the single step parser
*
* */
  def writeToQueue(args: Array[String]) = {
    val prodFactory: ConnectionFactory = new ConnectionFactory
    prodFactory.setHost(settings.rabbitMQHost)
    val prodConnection: Connection = prodFactory.newConnection
    val prodchannel: Channel = prodConnection.createChannel
    prodchannel.queueDeclare(settings.writeQueue, true, false, false, null)

    for(filePath <- args) {
      val path = Paths.get(filePath)
      val filename = path.getFileName.toString
      val uri = s"""nmd://${if(filename.lastIndexOf(".") > -1) filename.substring(0,filename.lastIndexOf(".")) else filename}"""
      val message = TreeParserQueueMessage(
        treeUri = uri,
        treeFilePath = path.toAbsolutePath.toString,
        treeType = if(filePath.contains(".zip")) TreeType.Zip else TreeType.Unknown
      )
      val msgBytes = JsonSupport.writeUtf8(message)
      logger.info(s"Message: $message, bytes Array size: ${msgBytes.length}")
      prodchannel.basicPublish("", settings.writeQueue, MessageProperties.PERSISTENT_TEXT_PLAIN, msgBytes)
    }
    logger.info(s"Wrote to Queue: ${settings.writeQueue}")
    prodchannel.close
    prodConnection.close
  }
}