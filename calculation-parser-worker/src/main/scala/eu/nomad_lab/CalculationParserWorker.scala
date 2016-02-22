package eu.nomad_lab

import java.io._
import java.nio.file.{Paths, Path}

import com.rabbitmq.client._
import com.typesafe.config.{ConfigFactory, Config}
import com.typesafe.scalalogging.StrictLogging
import eu.nomad_lab.QueueMessage.{CalculationParserQueueMessage, ToBeNormalizedQueueMessage}
import eu.nomad_lab.parsing_queue.CalculationParser
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipFile}
import org.apache.commons.compress.archivers.{ArchiveInputStream, ArchiveStreamFactory}
import org.apache.commons.compress.utils.IOUtils
import org.json4s.JsonAST.{JString, JObject, JValue}
import org.json4s._

object CalculationParserWorker extends  StrictLogging {

  /** The settings required to get the read, write queue, parsed and uncompressed Root
    */
  class Settings (config: Config) {
    // validate vs. reference.conf
    config.checkValid(ConfigFactory.defaultReference(), "simple-lib")

    val readQueue = config.getString("nomad_lab.calculation_parser_worker.readQueue")
    val writeQueue = config.getString("nomad_lab.calculation_parser_worker.writeQueue")
    val uncompressRoot = config.getString("nomad_lab.calculation_parser_worker.uncompressRoot")
    val parsedRoot = config.getString("nomad_lab.calculation_parser_worker.parsedRoot")

    def toJValue: JValue = {
      import org.json4s.JsonDSL._;
      ( ("readQueue" -> readQueue) ~
        ("writeQueue" -> writeQueue) ~
        ("uncompressRoot"  ->uncompressRoot)~
        ("parsedRoot"  ->parsedRoot))
    }
  }
  val settings = new Settings(ConfigFactory.load())
  val parserCollection = parsers.AllParsers.defaultParserCollection

  def initializeNextQueue(message: ToBeNormalizedQueueMessage) = {
    val prodFactory: ConnectionFactory = new ConnectionFactory
    prodFactory.setHost("localhost")
    val prodConnection: Connection = prodFactory.newConnection
    val prodchannel: Channel = prodConnection.createChannel
    prodchannel.queueDeclare(settings.writeQueue, true, false, false, null)
    val msgBytes = JsonSupport.writeUtf8(message)
    prodchannel.basicPublish("", settings.writeQueue, MessageProperties.PERSISTENT_TEXT_PLAIN,msgBytes )
    logger.info(s"Wrote to Queue: ${settings.writeQueue} $msgBytes")
    prodchannel.close
    prodConnection.close
  }


  val calculationParser = new CalculationParser(
    ucRoot = settings.uncompressRoot,
    parsedRoot = settings.parsedRoot,
    parserCollection = parsers.AllParsers.defaultParserCollection
  )
  def main(args: Array[String]): Unit = {
    //Example message for a single parser Parser
//    val message = CalculationParserQueueMessage(
//      parserName = "CastepParser", //Name of the parser to use for the file; CastepParser
//      mainFileUri = "nmd://R9h5Wp_FGZdsBiSo5Id6pnie_xeIH/data/examples/foo/Si2.castep", //Uri of the main file; Example:  nmd://R9h5Wp_FGZdsBiSo5Id6pnie_xeIH/data/examples/foo/Si2.castep
//      relativeFilePath = "examples/bla/fun/ran/Si2.castep", // file path, from the tree root. Example data/examples/foo/Si2.castep
//      treeFilePath = "/home/kariryaa/NoMad/nomad-lab-base/tree-parser-worker/examples.zip", // Same as the treeFilePath in TreeParserQueueMessage; eg. /nomad/nomadlab/raw_data/data/R9h/R9h5Wp_FGZdsBiSo5Id6pnie_xeIH.zip
//      treeType = TreeType.Zip
//    )
    readFromQueue()

  }

  def readFromQueue() = {
    val factory: ConnectionFactory = new ConnectionFactory
    factory.setHost("localhost")
    val connection: Connection = factory.newConnection
    val channel: Channel = connection.createChannel
    channel.queueDeclare(settings.readQueue, true, false, false, null)
    logger.info(s"Reading from Queue: ${settings.readQueue}")
    val consumer: Consumer = new DefaultConsumer((channel)) {
      @throws(classOf[IOException])
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]) {
        val message: CalculationParserQueueMessage = eu.nomad_lab.JsonSupport.readUtf8[CalculationParserQueueMessage](body)
        System.out.println(" [x] Received '" + message + "'")
//        uncompress(message)
        val resultMessage = calculationParser.uncompressAndInitializeParser(message)
        initializeNextQueue(resultMessage)
      }
    }
    channel.basicConsume(settings.readQueue, true, consumer)
  }

}