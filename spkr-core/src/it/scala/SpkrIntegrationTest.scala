package com.wajam.spkr.integration

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, FunSuite}
import org.apache.commons.configuration.{Configuration, PropertiesConfiguration}
import org.junit.Ignore
import com.wajam.spkr.config.SpkrConfig
import com.wajam.spkr.cluster.{Services, StaticSpkrClusterCreator}
import com.wajam.commons.Logging
import com.wajam.spkr.mry.{MryCalls, MrySpkrDatabase}

/**
 *
 */
@Ignore
class SpkrIntegrationTest extends FunSuite with BeforeAndAfterAll with BeforeAndAfter with Logging {

  protected val config: SpkrConfig = new SpkrConfig(loadConfiguration())

  protected def port: Int = config.getSpkListenPort

  protected val services = createServices()

  private def createServices(): Services = {
    val db:MrySpkrDatabase = new MrySpkrDatabase("IT_TEST", config)
    db.mysqlStorage.nuke()
    db.mysqlStorage.stop()

    // Create the Integration Test cluster
    StaticSpkrClusterCreator.buildStaticCluster(config)
  }

  override protected def beforeAll() {
    // Start the cluster
    services.cluster.start()
    services.scnClient.start()
  }

  override protected def afterAll() {
    services.cluster.stop()
  }

  def loadConfiguration(): Configuration = {
    new PropertiesConfiguration( "etc/spkr.integration.test.properties")
  }
}