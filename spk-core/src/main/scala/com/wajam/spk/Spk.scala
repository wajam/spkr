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
import org.apache.log4j.PropertyConfigurator
import java.net.URL
import com.wajam.nrv.Logging
import com.wajam.nrv.extension.json.codec.JsonCodec
import com.wajam.spk.mry.percolation.SpkPercolator
import com.wajam.spnl.ZookeeperTaskPersistenceFactory
import com.wajam.nrv.service.Service

/**
 *  Main object. It creates an instance of SpkServer (see below), and launches it.
 *  Creating the server here allows us to add a hook on application shutdown, and also to catch exceptions.
 */
object Spk extends App with Logging {
  try {
    // Spk uses the log4j library to output logs to the console. See the 'log4j.configuration' file for log4j configuration, in '/spk/etc/'.
    PropertyConfigurator.configureAndWatch(new URL(System.getProperty("log4j.configuration")).getFile, 5000L)
    val config = SpkConfig.fromDefaultConfigurationPath
    val server: SpkServer = new SpkServer(config)
    // A hook is added to the process, so it may terminate properly when the process is killed. See the stop() method for more info.
    sys.addShutdownHook(server.stop())
    // Once the server has been initialized properly, it starts itself as well as its sub-components.
    server.startAndBlock()
  } catch {
    case e: Exception => {
      error("Fatal error starting spk. Exiting spk server.", e)
      System.exit(1)
    }
  }

  /**
   * This class will initialize the spk server properly, by creating all dependencies and running them.
   */
  private class SpkServer(config: SpkConfig) extends Logging  {
    // First, we create the local node. Every instance of Spk in our distributed cluster will be associated with a
    // unique network ip and a set of ports described as a 'Node'. A unique port is required for each protocol used by
    // Spk. For spk, we'll use  2 protocols: nrv and http_api (2 ports required).
    //  - The 'nrv' protocol is required by the nrv framework in order to redirect each message to the appropriate node.
    //  - The 'http_api' protocol is a custom spk protocol similar to an HTTP REST protocol. We'll use it to query data
    //    from the javascript browser client.
    // TODO: show an example of a cluster with multiple nodes (multiple configuration file needed, or different configs)
    val node = new LocalNode(config.getSpkListenAddress, Map("nrv" -> config.getNrvListenPort, "http_api" -> config.getSpkListenPort))
    info("Local node is %s".format(node))

    // To keep all the nodes in the cluster synchronized together, nrv uses 'Zookeeper' to centralize important
    // configurations. You should already have zookeeper installed. If not, check out the setup instructions.
    // Assuming zookeeper is setup properly and running, we'll create a zookeeper client to access zookeeper, and we'll
    // use it to create the ClusterManager, which manages the all service members.
    // TODO: link documentation on service members, explain what the cluster manager can be used for, simplify demo with StaticClusterManager
    info("Creating zookeeper cluster manager...")
    val zookeeper = new ZookeeperClient(config.getZookeeperServers)
    val clusterManager = new ZookeeperClusterManager(zookeeper)

    // Next, we'll instance our nrv cluster. The cluster is the main nrv entry point that aggregates all the components
    // required to make our Spk distributed service work. Its primary function is to manages its different services.
    info("Creating cluster...")
    val cluster = new Cluster(
      localNode = node,
      clusterManager = clusterManager,
      actionSupportOptions = new ActionSupportOptions(
        switchboard = Some(new Switchboard(numExecutor = 200,maxTaskExecutorQueueSize = 50)),
        tracer = Some(new Tracer(NullTraceRecorder))),
      Some(new NrvProtocol(node,idleConnectionTimeoutMs =  10000,maxConnectionPoolSize = 100))
    )

    // Now that the cluster is created, we'll define the distributed service that we want it to support. The services
    // will be appended to the following list. We'll use a mutable list for better readability.
    var services: List[Service] = List()

    // 1) MRY Service
    // We create the mry database. A 'Database' is a specialized form of distributed service (nrv service) defined
    // in mry. The mry Database type is extended in spk and is called MrySpkDatabase. This custom database class also
    // defines the tables we'll need to store Spk's data. See MrySpkDatabase for more info.
    info("Creating mry database service...")
    val mrySpkServiceName = "mry.spk"
    val mryDb = new MrySpkDatabase(mrySpkServiceName, config)
    mryDb.applySupport(switchboard = Some(
      new Switchboard(
        name = "mry",
        numExecutor =  200,
        maxTaskExecutorQueueSize =  50,
        banExpirationDuration =  30000L,
        banThreshold = 0.40)
      )
    )
    services ::= mryDb

    // 2) SCN Service
    // We create and initialize our scn client (sequence number generator framework) that will be used to create unique IDs.
    // Scn is a distributed scalable service build on nrv.
    info("Creating scn client and service...")
    val scn = new Scn(
      serviceName = "scn",
      ScnConfig(
        timestampSaveAheadMs = 5000,
        timestampSaveAheadRenewalMs =  1000,
        sequenceSaveAheadSize = 1000
      ),
      storageType = StorageType.ZOOKEEPER,
      zookeeperClient = Some(zookeeper)
    )
    scn.applySupport(switchboard = Some(new Switchboard(numExecutor = 200,maxTaskExecutorQueueSize = 50)))
    val scnClient = new ScnClient(scn, ScnClientConfig(executionRateInMs = 10, timeoutInMs = 1000, numExecutor = 100))
    cluster.registerService(scn)
    // Warm up scn client
    scnClient.fetchTimestamps(mrySpkServiceName, (_, _) => Unit, 1, 0)

    // 3) SPK Service
    // We create our http_api (HTTP REST) Protocol, and register it as protocol handled by the cluster. A Json codec
    // is used as it will make it easy to parse the returned data in our javascript browser client.
    // See the SpkService class for implementation details of the spk service that basically acts as a bridges between
    // the mry service and the javascript client.
    info("Creating spk protocol and service...")
    val spkProtocol = new HttpProtocol("http_api", node, idleConnectionTimeoutMs = 10000, maxConnectionPoolSize = 100)
    spkProtocol.registerCodec("application/json", new JsonCodec)
    cluster.registerProtocol(spkProtocol)
    services ::= new SpkService("api.spk", mryDb, spkProtocol, scnClient)

    // All three services (mry, scn, spk) have been created and will now be registered in the cluster,
    info("registering all %s services in the cluster...".format(services.size))
    services.foreach(cluster.registerService(_))

    // We then create the percolation manager. Percolation is a mechanic used to replace database table relations that
    // would be very inefficient if implemented on a distributed store. It modifies the store based on certain events.
    // See the 'SpkPercolator' class for more details.
    // The created 'SpkPercolator' object will observe the database service and start when the right server member goes up.
    info("Creating percolation tasks...")
    new SpkPercolator(mryDb,scnClient,new ZookeeperTaskPersistenceFactory(zookeeper))

    /**
     * This method is called in a shutdown hook to shutdown dependencies. Although it is not necessary to execute this
     * when running a single node, it is generally a good idea to call it before shutting down a single node in a larger
     * cluster. Calling this method will allow all critical tasks, such as the data replication (not used in this demo)
     * to terminate properly.
     */
    def stop() {
      info("Stopping spk server...")
      cluster.stop(timeOutInMs = 5000)
      mryDb.stop()
    }

    /**
      * This method must be called before the server may handle http_api queries.
      */
    def startAndBlock() {
      start()
      Thread.currentThread.join()
    }

    /**
     *  Starts the sever
     *  Most components used in spk implement this pattern: A call to start() will usually start the element as well as
     *  all the elements it aggregates
     */
    private def start() {
      info("Starting spk server...")
      cluster.start()
      scnClient.start()
      info("Spk server started.")
    }
  }
}
