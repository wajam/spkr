package com.wajam.spkr.config

import org.apache.commons.configuration._

/**
 * This class accesses the configuration file.
 */
class SpkrConfig(config: Configuration) {

  def getMryMysqlServer: String = config.getString("spkr.mry.mysql.server")
  def getMryMysqlDb: String = config.getString("spkr.mry.mysql.schema")
  def getMryMysqlUsername: String = config.getString("spkr.mry.mysql.username")
  def getMryMysqlPassword: String = config.getString("spkr.mry.mysql.password")

  def getNrvListenPort: Int = config.getInt("spkr.nrv.listen.port")
  def getSpkListenPort: Int = config.getInt("spkr.api.listen.port")
  def getSpkListenAddress: String = config.getString("spkr.listen.address")

  def getZookeeperServers: String = config.getStringArray("spkr.zookeeper.servers").mkString(",")

  def isStaticCluster: Boolean = config.getBoolean("spkr.cluster.static")
  def getStaticClusterMryMembers: Seq[String] = { Seq(config.getStringArray("spkr.cluster.static.mry.members"): _*) }
  def getStaticClusterScnMembers: Seq[String] = { Seq(config.getStringArray("spkr.cluster.static.scn.members"): _*) }
}

/**
 * A SpkrConfig creator object. It may use the default file or a custom specified file.
 */
object SpkrConfig {
  private val defaultConfigurationPath = "etc/spkr.properties"

  /**
   * A fabric method instantiating a configuration using the default config file.
   */
  def fromDefaultConfigurationPath: SpkrConfig = {
    fromConfigurationPath(defaultConfigurationPath)
  }

  /**
   * A fabric method used to create a configuration from a custom config file.
   */
  def fromConfigurationPath(path: String): SpkrConfig = {
    val config = new PropertiesConfiguration(path)

    new SpkrConfig(config)
  }
}

