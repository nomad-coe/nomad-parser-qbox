package eu.nomad_lab

import java.util
import java.util.Properties

import akka.actor.{Actor, Props, ActorSystem}
import com.typesafe.scalalogging.StrictLogging

import eu.nomad_lab.parsers.CandidateParser
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveInputStream,ZipFile}
import org.apache.commons.compress.archivers.{ArchiveStreamFactory, ArchiveInputStream, ArchiveEntry}
import org.apache.tika.Tika
import scala.annotation.tailrec
import com.rabbitmq.client._
import java.io._
import eu.nomad_lab.QueueMessage

object TreeParser extends StrictLogging {

  val tika = new Tika() // Remove dependence on Tika as Zip is not supported anymore
//  val pConf:Properties = eu.nomad_lab.kafka.KafkaConfig.createProducerProps()
//  val cConf:Properties = eu.nomad_lab.kafka.KafkaConfig.createConsumerProps("127.0.0.1:9092","newgupLLALAasdsad")
//  val kafkaProducer:KafkaProducer[String,String] = new KafkaProducer[String,String](pConf)
//  val kafkaConsumer:KafkaConsumer[String,String] = new KafkaConsumer[String,String](cConf)
//  kafkaConsumer.subscribe(util.Arrays.asList("testTopic"));
//  val topic = "testTopic"
  val archieveStreamFactory = new ArchiveStreamFactory()
  val parserCollection = parsers.AllParsers.defaultParserCollection
  val readQueue = "TreeParserInitializationQueue1"
  val writeQueue = "SingleParserInitializationQueue1"


  def main(args: Array[String]) = {
//    readFromTreeParserQueue()
    val tempMessage =  QueueMessage.TreeParserQueueMessage("examples.tar","/home/kariryaa/NoMad/nomad-lab-base/tree-parser-worker/")
//    val mp = scanZipFile(tempMessage)


    val f = new File(tempMessage.treeFilePath+tempMessage.treeUri)
//    val fis:InputStream = new BufferedInputStream(new FileInputStream(f))
//    val ais: ArchiveInputStream =  archieveStreamFactory.createArchiveInputStream(fis)
//    val filesToUncompress:Map[String, String] = Map()
//    val scannedFiles:Map[String, String] = scanArchivedInputStream(filesToUncompress,ais)
//    logger.info("All extracted files: " + scannedFiles )
//    ais.close()
//    fis.close()

    val tempBIs:InputStream = new BufferedInputStream(new FileInputStream(f))
    val buf = Array.fill[Byte](8*1024)(0)
    val nRead = parserCollection.tryRead(tempBIs, buf, 0)
    val minBuf = buf.dropRight(buf.size - nRead)
    val mimeType: String = tika.detect(minBuf, f.getName)
    tempBIs.close()
    println(mimeType)
//    println(mp)
    ()
  }


  def readFromTreeParserQueue() = {
    val factory: ConnectionFactory = new ConnectionFactory
    factory.setHost("localhost")
    val connection: Connection = factory.newConnection
    val channel: Channel = connection.createChannel
    channel.queueDeclare(readQueue, true, false, false, null)
    logger.info(s"Reading from Queue: $readQueue")
    val consumer: Consumer = new DefaultConsumer((channel)) {
      @throws(classOf[IOException])
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]) {
        val message: QueueMessage.TreeParserQueueMessage = eu.nomad_lab.JsonSupport.readUtf8(body)
        System.out.println(" [x] Received '" + message + "'")
        findParserAndWriteToQueue(message)
      }
    }
    channel.basicConsume(readQueue, true, consumer)

  }

/** Find the parsable files and parsers. Write this information for the single step parser
*
* */
  def findParserAndWriteToQueue(incomingMessage: QueueMessage.TreeParserQueueMessage) = {

    //Read file and get the corresponding parsers
    val f = new File(incomingMessage.treeFilePath+incomingMessage.treeUri)
    if (!f.isFile)
      logger.error(f + " doesn't exist")
    else if (f.isDirectory)
      logger.error(f + "is a directory") //TODO: Handle directories
    else {

      val prodFactory: ConnectionFactory = new ConnectionFactory
      prodFactory.setHost("localhost")
      val prodConnection: Connection = prodFactory.newConnection
      val prodchannel: Channel = prodConnection.createChannel
      prodchannel.queueDeclare(writeQueue, true, false, false, null)
      //    To infer the type of the file before hand; At the moment the type has been fixed to tar and zip are handled correctly
      val tempBIs:InputStream = new BufferedInputStream(new FileInputStream(f))
      val buf = Array.fill[Byte](8*1024)(0)
      val nRead = parserCollection.tryRead(tempBIs, buf, 0)
      val minBuf = buf.dropRight(buf.size - nRead)
      val mimeType: String = tika.detect(minBuf, f.getName)
      tempBIs.close()
      var scannedFiles:Map[String,String] = Map()
      //Different formats should be handled differently; eg.  Zip can't be streamed
      if(mimeType.contains("zip"))
        {
          scannedFiles = scanZipFile(incomingMessage)
        }
      else if(mimeType.contains("tar")){
        val bis:InputStream = new BufferedInputStream(new FileInputStream(f))
        val ais: ArchiveInputStream =  archieveStreamFactory.createArchiveInputStream(bis)
        val filesToUncompress:Map[String, String] = Map()
        scannedFiles = scanArchivedInputStream(filesToUncompress,ais)
        logger.info("All extracted files: " + scannedFiles )
        ais.close()
        bis.close()

      }
      for( (filePath,parser) <- scannedFiles ) {
        val fileUri = incomingMessage.treeUri+filePath
        val completeFilePath =Some(incomingMessage.treeFilePath+incomingMessage.treeUri+filePath)
        val message = QueueMessage.SingleParserQueueMessage(parser,fileUri,completeFilePath,Some(incomingMessage.treeFilePath),mimeType)
        prodchannel.basicPublish("", writeQueue, MessageProperties.PERSISTENT_TEXT_PLAIN, JsonSupport.writeUtf8(message))
      }

      logger.info(s"Wrote to Queue: $writeQueue")
      prodchannel.close
      prodConnection.close
    }
  }

 @tailrec final def scanArchivedInputStream(filesToUncompress:Map[String, String], ais:ArchiveInputStream):Map[String, String] = {
   var filesToUC =  filesToUncompress
   Option(ais.getNextEntry) match {
      case Some(ae) =>
//          logger.info("Test")
//          logger.info("Can read Entry Data"+ais.canReadEntryData(ae))
//          logger.info("Get Byte read"+ais.getBytesRead)
//          logger.info("Entry Size"+ae.getSize)
//          logger.info("Bytes Size"+byte.size)
//          logger.info("Reading Bytes")

        if(ae.isDirectory){
          logger.info(ae.getName + ". It is a directory. Skipping it. Its children will be handled automatically in the recursion")
        }
        else {
          val buf = Array.fill[Byte](8*1024)(0)
          val nRead = parserCollection.tryRead(ais, buf, 0)
          val minBuf = buf.dropRight(buf.size - nRead)
          val candidateParsers = parserCollection.scanFile(ae.getName,minBuf).sorted
//          logger.info("Candidate Parsers:" + candidateParsers)
          if(candidateParsers.nonEmpty)
            filesToUC += (ae.getName->candidateParsers.head.parserName)
        }
        scanArchivedInputStream(filesToUC,ais)
      case _ =>
        filesToUncompress
    }
  }

/** Scan a zip file and if possible, find the most appropriate parser for each file in the zip file
*
* */
  def scanZipFile(incomingMessage: QueueMessage.TreeParserQueueMessage): Map[String,String] = {
    var fileParserName: Map[String,String] = Map()
    val zipFile = new ZipFile(incomingMessage.treeFilePath+incomingMessage.treeUri)
    val entries = zipFile.getEntries()
    while (entries.hasMoreElements()) {
      val zipEntry: ZipArchiveEntry = entries.nextElement()
      if (!zipEntry.isDirectory && !zipEntry.isUnixSymlink) {
        //Only check non directory and symlink for now; TODO: Add support to read symlink
        val zIn: InputStream = zipFile.getInputStream(zipEntry)
        val buf = Array.fill[Byte](8*1024)(0)
        val nRead = parserCollection.tryRead(zIn, buf, 0)
        val minBuf = buf.dropRight(buf.size - nRead)
        val candidateParsers = parserCollection.scanFile(zipEntry.getName, minBuf).sorted
        if (candidateParsers.nonEmpty)
        {
          fileParserName += (zipEntry.getName -> candidateParsers.head.parserName)
          logger.info(s"${zipEntry.getName} -> ${candidateParsers.head.parserName}")
        }
      }
    }
    fileParserName
  }


  //Kafka related stuff

  //Depreciated
  def pathsToExtract(filesToExtract: Map[String, Seq[CandidateParser]],mimeType:String): Set[String] = {
    var paths: Set[String] = Set()
    for( (filename,_) <-filesToExtract){
      val ind = filename.lastIndexOf(File.separatorChar)
      logger.info("Index:" + ind)
      if(ind > -1)
        paths += filename.substring(0,ind)
    }
    paths
  }
//Depreciated kept for temporary period
  def kafkaRelated() = {

    //    val topicPartition = kafkaConsumer.assignment()
    //    val partitionInfoIt = topicPartition.iterator()
    //    while (partitionInfoIt.hasNext) {
    //      val partitionInfo = partitionInfoIt.next()
    //     logger.info(s"PartitionInfo: ${partitionInfo.topic()} ${partitionInfo.partition()}")
    ////      kafkaConsumer.seekToBeginning(topic,1)
    //    }
    ////    kafkaConsumer.seek
    //    //Call the consumer and start reading messages
    //    val commitInterval = 2
    //    val buffer:util.ArrayList[ConsumerRecord[String, String]]  = new util.ArrayList[ConsumerRecord[String, String]]()
    //    var i = 0
    //    while (i < 20) {
    //      val records:ConsumerRecords[String, String] = kafkaConsumer.poll(100)
    //      logger.info("Read records are empty: "+ records.isEmpty)
    //      logger.info("Read records are empty: "+ records)
    //      val recordItr = records.iterator()
    //      while(recordItr.hasNext){
    //        val nxt = recordItr.next()
    //       logger.info("Record: " + nxt.key() + " ::: " + nxt.value() )
    //      }
    //      i+=1
    //    }

    /*

        val f = new File(args(0))
        if (!f.isFile)
          println(f + " doesn't exist")
        else if (f.isDirectory)
          println(f + "is a directory")
        else {
          val tempfis:InputStream = new BufferedInputStream(new FileInputStream(f))
          val bytePrefix =new Array[Byte](8000)
          tempfis.read(bytePrefix)
          val mimeType: String = tika.detect(bytePrefix, f.getName)
          tempfis.close()
          if(mimeType.contains("zip")){
            scanZipFile(f.getName)
          }
          else {
            val fis:InputStream = new BufferedInputStream(new FileInputStream(f))
            val ais: ArchiveInputStream =  archieveStreamfactory.createArchiveInputStream(fis)
            println("Created Archieve Input Stream:  "+ais.toString)

            println(mimeType)
            var filesToUncompress:Map[String, Seq[CandidateParser]] = Map()
            var filesToExtract= scanArchivedInputStream(filesToUncompress,ais)
            logger.info("All extracted files: "+filesToExtract )
            var paths:Set[String] = pathsToExtract(filesToExtract,mimeType)
            logger.info("All Paths"+ paths)
            for(p<-paths)
            {
              val topic = "testTopic"
              val key =" someRanfom"+ Math.random()
              val value = p
              val producerRecord:ProducerRecord[String,String]  = new ProducerRecord(topic, key, value)

              kafkaProducer.send(producerRecord)
    //            val byte = new Array[Byte](8000)
    //            kafkaConsumer.read(byte => println(byte))
            }
            ais.close()
            fis.close()
          }
        }*/

  }

}