package eu.nomad_lab

import com.typesafe.config.{Config, ConfigFactory}

object LocalEnv{
  def setup(config: Config): Unit = {
    //Rdb.defaultSettings_=(new Rdb.Settings(config))
  }
}
