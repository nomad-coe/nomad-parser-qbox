package eu.nomad_lab.parsers;
import com.typesafe.scalalogging.StrictLogging
import com.typesafe.config.{Config, ConfigFactory}
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

/** Methods to connect to the local relational DB
  */
object PythonParser extends StrictLogging {

  /** The settings required to connect to the local relational DB
    */
  class Settings (config: Config) {
    // validate vs. reference.conf
    config.checkValid(ConfigFactory.defaultReference(), "simple-lib")

    val pythonExe: String = config.getString("nomad_lab.parsers.python_exe")

    def toJson: JValue = {
      import org.json4s.JsonDSL._;
      ( ("python_exe"  -> pythonExe) )
    }
  }
}

class PythonParser(
  config: PythonParser.Settings
) {

}
