package eu.nomad_lab

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.{ConfigObject, ConfigValue, ConfigFactory, Config}
import scala.collection.JavaConverters._
import scala.collection.breakOut
import scala.collection.mutable
import java.util.Map.Entry
import org.{json4s => jsn}
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import com.typesafe.scalalogging.StrictLogging
import scala.util.control.NonFatal

object LocalEnv extends StrictLogging {
  val workerId = java.util.UUID.randomUUID.toString

  /** The settings required to connect to the local relational DB
    */
  class Settings (config: Config) {
    // validate vs. reference.conf
    config.checkValid(ConfigFactory.defaultReference(), "simple-lib")

    val baseTmpDir: String = config.getString("nomad_lab.baseTmpDir")
    val procTmpDir: Path = Paths.get(baseTmpDir, workerId)
    val replacements: Map[String, String] = {
      val baseRepl: Map[String, String] = config.getObject("nomad_lab.replacements").entrySet().asScala.map{
        (entry: Entry[String, ConfigValue]) =>
        entry.getKey() -> entry.getValue.unwrapped().toString()
      }(breakOut)
      baseRepl ++ Map(
        "workerId" -> workerId,
        "procTmpDir" -> procTmpDir.toString,
        "baseTmpDir" -> baseTmpDir
      )
    }

    def toJson: jsn.JValue = jsn.JObject(
      replacements.map{ case (k, v) =>
        k -> jsn.JString(v)
      }(breakOut): List[(String, jsn.JString)]
    )

  }

  private var privateDefaultSettings: Settings = null

  /** default settings, should be initialized on startup
    */
  def defaultSettings: Settings = {
    LocalEnv.synchronized {
      if (privateDefaultSettings == null) {
        privateDefaultSettings = new Settings(ConfigFactory.load())
      }
      privateDefaultSettings
    }
  }

  /** sets the default settings
    */
  def defaultSettings_=(newValue: Settings): Unit = {
    LocalEnv.synchronized {
      if (privateDefaultSettings != null)
        logger.warn(s"eu.nomad_lab.LocalEnv overwriting old settings ${JsonUtils.prettyStr(privateDefaultSettings.toJson)} with ${JsonUtils.prettyStr(newValue.toJson)}")
      privateDefaultSettings = newValue
    }
  }

  def setup(config: Config): Unit = {
    defaultSettings_=(new Settings(config))
  }

  val varToReplaceRe = """\$\{([a-zA-Z][a-zA-Z0-9]*)\}""".r
  /** replaces ${var} expressions that have a var -> value pair in the replacements with value
    *
    * Repeats replacements up to 3 times to allow the replacements themselves to use variables
    * that need expansion.
    */
  def makeReplacements(repl: Map[String, String], string: String): String = {
    var str: String = string
    for (irecursive <- 1.to(3)) {
      var i: Int = 0
      val s = new mutable.StringBuilder()
      for (m <- varToReplaceRe.findAllMatchIn(str)) {
        val varNow = m.group(1)
        repl.get(varNow) match {
          case Some(value) =>
            s ++= str.substring(i, m.start) // would subSequence be more efficient?
            s ++= value
            i = m.end
          case None =>
            ()
        }
      }
      if (i == 0) {
        return str
      } else {
        s ++= str.substring(i)
        str = s.toString()
      }
    }
    str
  }

  /** recursively deletes a directory (or a file)
    */
  def deleteRecursively(path: Path): Boolean = {
    val pathF = path.toFile()
    var hasErrors: Boolean = false
    if (pathF.isDirectory()) {
      Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor[Path]() {
        import java.nio.file.FileVisitResult
        import java.nio.file.attribute.BasicFileAttributes

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          try {
            Files.delete(file)
          } catch {
            case NonFatal(exc) =>
              logger.warn(s"error while deleting $file in $path: $exc")
              hasErrors = true
          }
          return FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: java.io.IOException): FileVisitResult = {
          if (exc == null) {
            Files.delete(dir)
          } else {
            logger.warn(s"error while deleting $dir in $path: $exc")
            hasErrors = true
          }
          return FileVisitResult.CONTINUE
        }
      })
    } else if (pathF.isFile()) {
      try {
        Files.delete(path)
      } catch {
        case NonFatal(exc) =>
          logger.warn(s"error while deleting $path: $exc")
          hasErrors = true
      }
    } else {
      logger.warn(s"requested recursive delete of non existing path $path")
      hasErrors = true
    }
    !hasErrors
  }
}
