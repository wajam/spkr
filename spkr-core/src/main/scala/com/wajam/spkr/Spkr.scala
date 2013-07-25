package com.wajam.spkr

import com.wajam.spkr.config.SpkrConfig
import com.wajam.spkr.mry.{SpkrService, MrySpkrDatabase}
import com.wajam.nrv.cluster.{StaticClusterManager, ClusterManager, Cluster, LocalNode}
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
import com.wajam.spkr.mry.percolation.SpkrPercolator
import com.wajam.spnl.{NoTaskPersistence, ZookeeperTaskPersistenceFactory, NoTaskPersistenceFactory}
import com.wajam.nrv.service.Service

/**
 *  Main object. It creates an instance of SpkServer (see below), and launches it.
 *  Creating the server here allows us to add a hook on application shutdown, and also to catch exceptions.
 */
object Spkr extends App with Logging {

  try {
    // Spkr uses the log4j library to output logs to the console. See the 'log4j.configuration' file for log4j configuration, in '/spkr/etc/'.
    PropertyConfigurator.configureAndWatch(new URL(System.getProperty("log4j.configuration")).getFile, 5000L)
    val config = SpkrConfig.fromDefaultConfigurationPath
    val server: SpkServer = new SpkServer(config)

    // A hook is added to the process, so it may terminate properly when the process is killed. See the stop() method for more info.
    sys.addShutdownHook(server.stop())

    // Once the server has been initialized properly, it starts itself as well as its sub-components.
    server.startAndBlock()
  } catch {
    case e: Exception => {
      error("Fatal error starting spkr. Exiting spkr server.", e)
      System.exit(1)
    }
  }

  /**
   * This class will initialize the spkr server properly, by creating all dependencies and running them.
   */
  private class SpkServer(config: SpkrConfig) extends Logging  {
    // First, we create the local node. Every instance of Spkr in our distributed cluster will be associated with a
    // unique network ip and a set of ports described as a 'Node'. A unique port is required for each protocol used by
    // Spkr. For spkr, we'll use  2 protocols: nrv and http_api (2 ports required).
    //  - The 'nrv' protocol is required by the nrv framework in order to redirect each message to the appropriate node.
    //  - The 'http_api' protocol is a custom spkr protocol similar to an HTTP REST protocol. We'll use it to query data
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
    val zookeeper = config.isStaticCluster match {
      case true => None
      case false => Some(new ZookeeperClient(config.getZookeeperServers))
    }
    val clusterManager = config.isStaticCluster match {
      case true => new StaticClusterManager
      case false => new ZookeeperClusterManager(zookeeper.get)
    }

    // Next, we'll instance our nrv cluster. The cluster is the main nrv entry point that aggregates all the components
    // required to make our Spkr distributed service work. Its primary function is to manages its different services.
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
    // in mry. The mry Database type is extended in spkr and is called MrySpkrDatabase. This custom database class also
    // defines the tables we'll need to store Spkr's data. See MrySpkrDatabase for more info.
    info("Creating mry database service...")
    val mrySpkServiceName = "mry.spkr"
    val mryDb = new MrySpkrDatabase(mrySpkServiceName, config)
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
    //TODO: seperate this logic
    clusterManager match {
      case staticClusterManager: StaticClusterManager => {
        staticClusterManager.addMembers(mryDb, config.getStaticClusterMryMembers)
      }
      case _ =>
    }

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
      storageType = config.isStaticCluster match {
        case true => StorageType.MEMORY
        case false => StorageType.ZOOKEEPER
      },
      zookeeperClient = zookeeper
    )
    scn.applySupport(switchboard = Some(new Switchboard(numExecutor = 200,maxTaskExecutorQueueSize = 50)))
    val scnClient = new ScnClient(scn, ScnClientConfig(executionRateInMs = 10, timeoutInMs = 1000, numExecutor = 100))
    cluster.registerService(scn)
    // Warm up scn client
    //scnClient.fetchTimestamps(mrySpkServiceName, (_, _) => Unit, 1, 0)
    services ::= scn
    //TODO: seperate this logic
    clusterManager match {
      case staticClusterManager: StaticClusterManager => {
        staticClusterManager.addMembers(scn, config.getStaticClusterScnMembers)
      }
      case _ =>
    }

    // 3) SPKR Service
    // We create our http_api (HTTP REST) Protocol, and register it as protocol handled by the cluster. A Json codec
    // is used as it will make it easy to parse the returned data in our javascript browser client.
    // See the SpkrService class for implementation details of the spkr service that basically acts as a bridges between
    // the mry service and the javascript client.
    info("Creating spkr protocol and service...")
    val spkProtocol = new HttpProtocol("http_api", node, idleConnectionTimeoutMs = 10000, maxConnectionPoolSize = 100)
    spkProtocol.registerCodec("application/json", new JsonCodec)
    cluster.registerProtocol(spkProtocol)
    val spkService = new SpkrService("api.spkr", mryDb, spkProtocol, scnClient)
    services ::= spkService

    // All three services (mry, scn, spkr) have been created and will now be registered in the cluster,
    info("registering all %s services in the cluster...".format(services.size))
    services.foreach(cluster.registerService(_))

    // We then create the percolation manager. Percolation is a mechanic used to replace database table relations that
    // would be very inefficient if implemented on a distributed store. It modifies the store based on certain events.
    // See the 'SpkrPercolator' class for more details.
    // The created 'SpkrPercolator' object will observe the database service and start when the right server member goes up.
    info("Creating percolation tasks...")
    new SpkrPercolator(mryDb,scnClient,spnlPersistence = config.isStaticCluster match {
      case true => new NoTaskPersistenceFactory
      case false => new ZookeeperTaskPersistenceFactory(zookeeper.get)
    })

    /**
     * This method is called in a shutdown hook to shutdown dependencies. Although it is not necessary to execute this
     * when running a single node, it is generally a good idea to call it before shutting down a single node in a larger
     * cluster. Calling this method will allow all critical tasks, such as the data replication (not used in this demo)
     * to terminate properly.
     */
    def stop() {
      info("Stopping spkr server...")
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
     *  Most components used in spkr implement this pattern: A call to start() will usually start the element as well as
     *  all the elements it aggregates
     */
    private def start() {
      info("Starting spkr server...")
      cluster.start()
      scnClient.start()
      info("Spkr server started.")
    }
  }
}
