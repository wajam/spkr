package com.wajam.spk

import com.wajam.spk.config.SpkConfig
import com.wajam.spk.mry.{SpkService, MrySpkDatabase}
import com.wajam.nrv.cluster.{Cluster, LocalNode}
import com.wajam.nrv.zookeeper.cluster.ZookeeperClusterManager
import com.wajam.nrv.service.{Switchboard, ActionSupportOptions}
import com.wajam.nrv.tracing.{NullTraceRecorder, Tracer}
import com.wajam.nrv.protocol.{HttpProtocol, NrvProtocol}
import com.wajam.nrv.zookeeper.ZookeeperClient
import com.wajam.scn.{Scn, ScnConfig}
import com.wajam.scn.storage.StorageType
import com.wajam.scn.client.{ScnClientConfig, ScnClient}
import com.wajam.nrv.consistency.{ConsistencyMasterSlave, ConsistencyOne}
import org.apache.log4j.PropertyConfigurator
import java.net.URL
import com.wajam.nrv.Logging
import com.wajam.nrv.extension.json.codec.JsonCodec

/**
 *  This main class creates and launch the spk server.
 */

object Spk extends App with Logging {
  try {
    PropertyConfigurator.configureAndWatch(new URL(System.getProperty("log4j.configuration")).getFile, 5000)
    val config = SpkConfig.fromDefaultConfigurationPath
    val server: SpkServer = new SpkServer(config)
    sys.addShutdownHook(server.stop())
    server.startAndBlock()
  } catch {
    case e: Exception => {
      println("Fatal error starting spk. Exiting spk server.", e)
      System.exit(1)
    }
  }

  /**
   * This class will initialize the spk server properly, by creating all dependencies and running them.
   */
  //TODO: validate every step and diplay a good error message.
  private class SpkServer(config: SpkConfig) extends Logging  {
    // Create local node
    val node = new LocalNode(config.getSpkListenAddress, Map("nrv" -> config.getNrvListenPort, "spk_http" -> config.getSpkListenPort))
    println("Local node is %s".format(node))

    // MRY database
    println("creating mry database...")
    val mrySpkServiceName = "mry.spk"
    val mryDb = new MrySpkDatabase(mrySpkServiceName, config)

    // Zookeeper cluster manager
    println("creating zookeeper cluster manager...")
    val zookeeper = new ZookeeperClient(config.getZookeeperServers)
    val clusterManager = new ZookeeperClusterManager(zookeeper)

    // Cluster
    println("creating cluster...")
    val cluster = new Cluster(
      node,
      clusterManager,
      new ActionSupportOptions(
        switchboard = Some(new Switchboard(numExecutor = 200,maxTaskExecutorQueueSize = 50)),
        tracer = Some(new Tracer(NullTraceRecorder))),
      Some(new NrvProtocol(node, 10000,100))
    )

    // HTTP REST Protocol
    val spkProtocol = new HttpProtocol("spk_http", node, idleConnectionTimeoutMs = 10000, maxConnectionPoolSize = 100)
    spkProtocol.registerCodec("application/json", new JsonCodec)
    cluster.registerProtocol(spkProtocol)

    // Sequence number generator
    println("creating and registering scn client...")
    val scn = new Scn("scn", ScnConfig(5000, 1000, 1000), StorageType.ZOOKEEPER, Some(zookeeper))
    scn.applySupport(switchboard = Some(new Switchboard(numExecutor = 200,maxTaskExecutorQueueSize = 50)))
    val scnClient = new ScnClient(scn, ScnClientConfig(10,1000,100))
    cluster.registerService(scn)

    // Warmup scn client
    scnClient.fetchTimestamps(mrySpkServiceName, (_, _) => Unit, 1, 0)

    // Spk service
    val consistency = new ConsistencyMasterSlave(scnClient, "./logs", false) //This is necessary to write in mry (consistent db)
    cluster.registerService(mryDb)
    mryDb.applySupport(switchboard = Some(new Switchboard("mry", 200, 50, 30000L, 0.40)), consistency = Some(consistency))
    consistency.bindService(mryDb)
    cluster.registerService(new SpkService("api.spk", mryDb, spkProtocol,scnClient))

    /**
     * This method is called in a shutdown hook to shutdown dependencies. Although it is not necessary to execute this
     * when running a single node, it is generally a good idea to call it when shutting down a single node in a larger
     * cluster. Calling this method will allow all critical tasks, such as the data replication (not used in this application)
     * to end properly.
     */
    def stop() {
      println("stopping spk server...")
      cluster.stop(timeOutInMs = 5000)
      mryDb.stop()
    }

    /**
      * This method must be called before the server may handle spk_http queries.
      */
    def startAndBlock() {
      start()
      Thread.currentThread.join()
    }
    private def start() {
      println("starting spk server...")
      cluster.start()
      scnClient.start()
      println("spk server started.")
    }
  }
}
