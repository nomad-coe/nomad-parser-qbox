package eu.nomad_lab.rdb

import org.jooq.impl.DSL
import com.typesafe.scalalogging.StrictLogging
import com.typesafe.config.{Config, ConfigFactory}
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

import org.jooq;
import java.sql.{Connection, DriverManager}
import scala.util.control.NonFatal

/** Methods to connect to the local relational DB
  */
object Rdb extends StrictLogging {

  /** The settings required to connect to the local relational DB
    */
  class Settings (config: Config) {
    // validate vs. reference.conf
    config.checkValid(ConfigFactory.defaultReference(), "simple-lib")

    val username: String = config.getString("nomad_lab.rdb.username")
    val password: String = config.getString("nomad_lab.rdb.password")
    val jdbcUrl: String = config.getString("nomad_lab.rdb.jdbcUrl")

    def toJson: JValue = {
      import org.json4s.JsonDSL._;
      ( ("username" -> username) ~
        ("password" -> password) ~
        ("jdbcUrl"  ->jdbcUrl) )
    }
  }

  private var privateDefaultSettings: Settings = null

  /** default settings, should be initialized on startup
    */
  def defaultSettings(): Settings = {
    Rdb.synchronized {
      if (privateDefaultSettings == null) {
        logger.info(s"eu.nomad_lab.Rdb creating default settings")
        privateDefaultSettings = new Settings(ConfigFactory.load())
      }
      privateDefaultSettings
    }
  }

  /** sets the default settings
    */
  def defaultSettings_=(newValue: Settings): Unit = {
    Rdb.synchronized {
      if (privateDefaultSettings != null)
        logger.warn(s"eu.nomad_lab.Rdb overwriting old settings ${JsonUtils.prettyStr(privateDefaultSettings.toJson)} with ${JsonUtils.prettyStr(newValue.toJson)}")
      privateDefaultSettings = newValue
    }
  }

  /** Error connected to the DB (for example connection failed)
    */
  class DBError(msg:String, reason: Throwable) extends Exception(msg, reason) { }

  /** Creates a new connection to the DB
    */
  def newConnection(settings: Settings): Connection = {
    try {
      DriverManager.getConnection(settings.jdbcUrl, settings.username, settings.password)
    } catch {
      case NonFatal(e) => 
        throw new DBError(s"failed to connect to the db using ${JsonUtils.prettyStr(settings.toJson)}", e)
    }
  }

  /** Returns a jooq context connected to the DB
    */
  def newContext(settings: Settings): jooq.DSLContext = {
    val conn = newConnection(settings)
    jooq.impl.DSL.using(conn, jooq.SQLDialect.H2)
  }

  /** creates an Rdb context with the default settings
    */
  def apply(): Rdb = {
    new Rdb(newContext(defaultSettings()))
  }

  /** creates an Rdb context with the given settings
    */
  def apply(settings: Settings): Rdb = {
    new Rdb(newContext(settings))
  }
}

/** Represents a connection with the Rdb database
  */
class Rdb(
  val dbContext: jooq.DSLContext
)
