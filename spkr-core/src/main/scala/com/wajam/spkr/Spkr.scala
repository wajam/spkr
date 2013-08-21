package com.wajam.spkr

import com.wajam.spkr.config.SpkrConfig
import org.apache.log4j.PropertyConfigurator
import java.net.URL
import com.wajam.nrv.Logging
import com.wajam.spkr.cluster.{ZookeeperSpkrClusterCreator, StaticSpkrClusterCreator, Services}
import java.io.File

/**
 *  Main object. It creates an instance of SpkServer (see below), and launches it.
 *  Creating the server here allows us to add a hook on application shutdown (so it may terminate properly when the
 *  process is killed. See the stop() method for more info.), and also to catch exceptions.
 */
object Spkr extends App with Logging {

    try {
      // Init log4j. See 'log4j.configuration' in '/spkr/etc/' to edit config.
      val logFile = System.getProperty("log4j.configuration", "file://" + new File("etc/log4j.properties").getCanonicalPath)
      PropertyConfigurator.configureAndWatch(new URL(logFile).getFile, 5000L)
      val config = SpkrConfig.fromDefaultConfigurationPath
      val server: SpkServer = new SpkServer(config)

    sys.addShutdownHook(server.stop())

    server.startAndBlock()
  } catch {
    case e: Exception => {
      println("Fatal error starting SPKR. Exiting SPKR server.", e) // Print error even when logger unavailable
      error("Fatal error starting SPKR. Exiting SPKR server.", e)
      System.exit(1)
    }
  }

  /**
   * This class will initialize the SPKR server properly.
   * It uses the config file to build the right type of cluster (see config.isStaticCluster).
   * The static cluster is easier to setup, but the dynamic zookeeper cluster is required for cluster-aware nodes.
   */
  private class SpkServer(config: SpkrConfig) extends Logging  {
    val spkrServices: Services = config.isStaticCluster match {
      case true => StaticSpkrClusterCreator.buildStaticCluster(config)
      case false => ZookeeperSpkrClusterCreator.buildDynamicCluster(config)
    }

    /**
     * This method is called in a shutdown hook to shutdown dependencies. Although it is not necessary to execute this
     * when running a single node, it is generally a good idea to call it before shutting down a single node in a larger
     * cluster. Calling this method will allow all critical tasks, such as the data replication (not used in this demo)
     * to terminate properly.
     */
    def stop() {
      info("Stopping SPKR server...")
      spkrServices.cluster.stop(timeOutInMs = 5000)
    }

    /**
      * This method must be called before the server can handle http queries.
      */
    def startAndBlock() {
      start()
      Thread.currentThread.join()
    }

    /**
     *  Starts the sever
     *  Most components in NRV implement this pattern: A call to start() will start the element as well as
     *  all the elements it aggregates.
     */
    private def start() {
      info("Starting SPKR server...")
      spkrServices.cluster.start()
      spkrServices.scnClient.start()
      info("Now speaking.")
    }
  }
}
