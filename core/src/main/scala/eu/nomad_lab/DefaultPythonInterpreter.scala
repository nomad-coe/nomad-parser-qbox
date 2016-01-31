package eu.nomad_lab;
import org.{json4s => jn}
import com.typesafe.scalalogging.StrictLogging
import com.typesafe.config
import scala.collection.JavaConversions._
import scala.collection.breakOut

object DefaultPythonInterpreter extends StrictLogging {
    /** The settings required to connect to the local relational DB
    */
  class Settings (conf: config.Config) {
    // validate vs. reference.conf
    conf.checkValid(config.ConfigFactory.defaultReference(), "simple-lib")

    val python2Exe: String = conf.getString("nomad_lab.python.python2Exe")
    val commonFiles: Seq[String] = conf.getStringList("nomad_lab.python.commonFiles").toSeq
    val commonDirMapping: Map[String, String] = conf.getObject("nomad_lab.python.commonDirMapping").unwrapped().map {
      case (k, v) =>
        k -> (v match { case value: String => value })
    }(breakOut)

    def toJson: jn.JValue = {
      import org.json4s.JsonDSL._
      ("python2Exe" -> jn.JString(python2Exe)) ~
      ("commonFiles" -> commonFiles) ~
      ("commonDirMapping" -> commonDirMapping)
    }
  }

  private var privateDefaultSettings: Settings = null

  /** default settings, should be initialized on startup
    */
  def defaultSettings(): Settings = {
    DefaultPythonInterpreter.synchronized {
      if (privateDefaultSettings == null) {
        privateDefaultSettings = new Settings(config.ConfigFactory.load())
      }
      privateDefaultSettings
    }
  }

  def python2Exe(conf: Settings = null): String = {
    if (conf == null)
      defaultSettings().python2Exe
    else
      conf.python2Exe
  }

  def commonFiles(conf: Settings = null): Seq[String] = {
    if (conf == null)
      defaultSettings().commonFiles
    else
      conf.commonFiles
  }

  def commonDirMapping(conf: Settings = null): Map[String, String] = {
    if (conf == null)
      defaultSettings().commonDirMapping
    else
      conf.commonDirMapping
  }
}
