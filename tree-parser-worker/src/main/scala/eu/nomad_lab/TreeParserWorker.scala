package eu.nomad_lab

import com.typesafe.config.{ConfigFactory, Config}
import com.typesafe.scalalogging.StrictLogging
import eu.nomad_lab.QueueMessage.{TreeParserQueueMessage}
import eu.nomad_lab.parsing_queue.TreeParser.TreeParserException
import eu.nomad_lab.parsing_queue.{TreeParser}
import org.json4s.JsonAST.JValue
import com.rabbitmq.client._
import java.io._


object TreeParserWorker extends StrictLogging {

  /** The settings required to get the read and write queue
    */
  class Settings (config: Config) {
    // validate vs. reference.conf
    config.checkValid(ConfigFactory.defaultReference(), "simple-lib")

    val readQueue = config.getString("nomad_lab.parser_worker_rabbitmq.treeParserQueue")
    val writeQueue = config.getString("nomad_lab.parser_worker_rabbitmq.singleParserQueue")
    val rabbitMQHost = config.getString("nomad_lab.parser_worker_rabbitmq.rabbitMQHost")

    def toJValue: JValue = {
      import org.json4s.JsonDSL._;
      ( ("rabbitMQHost" -> rabbitMQHost) ~
        ("readQueue" -> readQueue) ~
        ("writeQueue" -> writeQueue))
    }
  }

  val settings = new Settings(ConfigFactory.load())
  val parserCollection = parsers.AllParsers.defaultParserCollection
  val treeParser = new TreeParser(
    parserCollection = parserCollection
  )

  def main(args: Array[String]): Unit = {

    readFromTreeParserQueue()

////    For sample trial without the read queue initialization
//    val tempMessage =  QueueMessage.TreeParserQueueMessage(
//      treeUri = "file:///home/kariryaa/NoMad/nomad-lab-base/tree-parser-worker/fhi.zip",
//      treeFilePath = "/home/kariryaa/NoMad/nomad-lab-base/tree-parser-worker/fhi.zip",
//      treeType = TreeType.Zip
//    )
//    findParserAndWriteToQueue(tempMessage)

  }


  def readFromTreeParserQueue(): Unit = {
    val factory: ConnectionFactory = new ConnectionFactory
    factory.setHost(settings.rabbitMQHost)
    val connection: Connection = factory.newConnection
    val channel: Channel = connection.createChannel

    //channel.queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments)
    channel.queueDeclare(settings.readQueue, true, false, false, null)
    logger.info(s"Reading from Queue: ${settings.readQueue}")

    // Fair dispatch: don't dispatch a new message to a worker until it has processed and acknowledged the previous one
    val prefetchCount = 1
    channel.basicQos(prefetchCount)

    val consumer: Consumer = new DefaultConsumer((channel)) {
      @throws(classOf[IOException])
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]) {
        val message: TreeParserQueueMessage = eu.nomad_lab.JsonSupport.readUtf8[TreeParserQueueMessage](body)
        println(" [x] Received '" + message + "'")
        //Send acknowledgement to the broker once the message has been consumed.
        try {
          findParserAndWriteToQueue(message)
        } finally {
          println(" [x] Done")
          channel.basicAck(envelope.getDeliveryTag(), false)
        }
      }
    }
    //basicConsume(String queue, boolean autoAck, Consumer callback)
    channel.basicConsume(settings.readQueue, false, consumer)
    ()
  }


/** Find the parsable files and parsers. Write this information for the single step parser
*
* */
  def findParserAndWriteToQueue(incomingMessage: TreeParserQueueMessage): Unit = {
    val msgList = treeParser.findParser(incomingMessage)
    if(msgList.isEmpty)
      throw new TreeParserException(incomingMessage, "No Parsable file found, ")
    else{
      val prodFactory: ConnectionFactory = new ConnectionFactory
      prodFactory.setHost(settings.rabbitMQHost)
      val prodConnection: Connection = prodFactory.newConnection
      val prodchannel: Channel = prodConnection.createChannel
      prodchannel.queueDeclare(settings.writeQueue, true, false, false, null)
      for(message <- msgList){
        val msgBytes = JsonSupport.writeUtf8(message)
        logger.info(s"Message: $message, bytes Array size: ${msgBytes.length}")
        prodchannel.basicPublish("", settings.writeQueue, MessageProperties.PERSISTENT_TEXT_PLAIN,msgBytes )
      }
      logger.info(s"Wrote to Queue: ${settings.writeQueue}")
      prodchannel.close
      prodConnection.close
    }
  }
}