package eu.nomad_lab

import java.util._
import kafka.producer._
import kafka.producer.ProducerConfig
import scala.util.Random

/**
  * Created by kariryaa on 12/18/15.
  */
object calculationParser {

  def main(args: Array[String]): Unit = {

    val rnd  = Random
    val props: Properties = new Properties()
    props.put("metadata.broker.list", "130.183.207.77:32776,130.183.207.77:32775,130.183.207.77:32774")
    props.put("serializer.class", "kafka.serializer.StringEncoder")
    props.put("request.required.acks", "1")

    val config:ProducerConfig = new ProducerConfig(props)
    val producer = new Producer[String,String](config)
    for (i <- 1 to 10) {
//      val runtime = Date
      val ip = "192.168.2." + rnd.nextInt(255)
      val msg =  """,www.example.com,""" + ip


      val data = new KeyedMessage[String, String]("topic", ip, msg);
      producer.send(data);
    }
    producer.close();
  }
}
