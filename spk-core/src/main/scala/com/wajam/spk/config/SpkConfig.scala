package com.wajam.spk.config

import org.apache.commons.configuration._

/**
 * This class accesses the configuration file.
 */
class SpkConfig(config: Configuration) {

  def getMryMysqlServer: String = config.getString("spk.mry.mysql.server")
  def getMryMysqlDb: String = config.getString("spk.mry.mysql.schema")
  def getMryMysqlUsername: String = config.getString("spk.mry.mysql.username")
  def getMryMysqlPassword: String = config.getString("spk.mry.mysql.password")

  def getNrvListenPort: Int = config.getInt("spk.nrv.listen.port")
  def getSpkListenPort: Int = config.getInt("spk.http.listen.port")
  def getSpkListenAddress: String = config.getString("spk.listen.address")

  def getZookeeperServers: String = config.getStringArray("spk.nrv.zookeeper.servers").mkString(",")
}

/**
 * An SpkConfig creator object. It may use the default file or a custom specified file.
 */
object SpkConfig {
  private val defaultConfigurationPath = "etc/spk.properties"

  /**
   * A fabric method instantiating a configuration using the default config file.
   */
  def fromDefaultConfigurationPath: SpkConfig = {
    fromConfigurationPath(defaultConfigurationPath)
  }

  /**
   * A fabric method used to create a configuration from a custom config file.
   */
  def fromConfigurationPath(path: String): SpkConfig = {
    val config = new PropertiesConfiguration(path)

    new SpkConfig(config)
  }
}

