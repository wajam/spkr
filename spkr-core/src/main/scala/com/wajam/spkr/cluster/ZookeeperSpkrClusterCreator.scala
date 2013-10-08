package com.wajam.spkr.cluster

import com.wajam.spkr.config.SpkrConfig
import com.wajam.commons.Logging
import com.wajam.nrv.cluster.{LocalNode, Cluster, StaticClusterManager}
import com.wajam.spkr.mry.{SpkrService, MrySpkrDatabase}
import com.wajam.scn.Scn
import com.wajam.scn.storage.StorageType
import com.wajam.scn.client.ScnClient
import com.wajam.nrv.extension.json.codec.JsonCodec
import com.wajam.spkr.mry.percolation.SpkrPercolator
import com.wajam.spnl.{ZookeeperTaskPersistenceFactory, NoTaskPersistenceFactory}
import com.wajam.nrv.zookeeper.ZookeeperClient
import com.wajam.nrv.zookeeper.cluster.ZookeeperClusterManager

/**
 *  Unlike the StaticSpkrClusterCreator, the cluster created by this function dynamically adapts itself at
 *  run time. There are 3 main differences:
 *  - A zookeeper client is created and used to initialize a ZookeeperClusterManager.
 *  - SCN uses zookeeper for persistence in Id generation from one session to another.
 *  - The percolator uses zookeeperPersistence to resume its tasks on restart (instead of starting over).
 */
object ZookeeperSpkrClusterCreator extends Logging  {

  def buildDynamicCluster(config: SpkrConfig): Services = {
    // 'Services' contains everything we need to run our Spkr server
    val services: Services = new Services
    services.config = config
    services.localNode = new LocalNode(config.getSpkListenAddress, Map("nrv" -> config.getNrvListenPort, "http_api" -> config.getSpkListenPort))
    info("Local node is %s".format(services.localNode))

    // Zookeeper used to coordinate multiple nodes
    info("Creating zookeeper client...")
    services.zookeeper = new ZookeeperClient(config.getZookeeperServers)

    // The zookeeper cluster manager will dynamically update its services using on zookeeper's content.
    info("Creating dynamic zookeeper cluster manager...")
    val clusterManager = new ZookeeperClusterManager(services.zookeeper)

    // The cluster manages all services and nodes.
    info("Creating cluster...")
    services.cluster = new Cluster(
      services.localNode,
      clusterManager,
      SpkrClusterContext.clusterSupport,
      SpkrClusterContext.createNrvProtocol(services.localNode)
    )

    // Service #1: MRY Service
    // A 'MrySpkrDatabase' is a specialized form of distributed service (nrv service) built on MRY.
    info("Creating mry database service...")
    services.mry = new MrySpkrDatabase("mry.spkr", config)
    services.mry.applySupportOptions(SpkrClusterContext.mrySupport)
    services.cluster.registerService(services.mry)

    // Service #2: SCN Service
    // SCN client (sequence number generator framework) for Id and timestamp generation.
    info("Creating scn client and service...")
    services.scn = new Scn("scn", SpkrClusterContext.scnConfig, StorageType.ZOOKEEPER, Some(services.zookeeper))
    services.scn.applySupportOptions(SpkrClusterContext.scnSupport)
    services.scnClient = new ScnClient(services.scn, SpkrClusterContext.scnClientConfig)
    services.cluster.registerService(services.scn)

    // Service #3: HTTP API Service
    // A custom service with an HTTP interface using Json encoding to make it easy to parse the returned data in our
    // browser client. See the SpkrService class for implementation details.
    info("Creating spkr protocol and service...")
    val spkProtocol = SpkrClusterContext.createHttpProtocol(services.localNode)
    spkProtocol.registerCodec("application/json", new JsonCodec)
    services.cluster.registerProtocol(spkProtocol)
    services.spkr = new SpkrService("api.spkr", services.mry, spkProtocol, services.scnClient)
    services.cluster.registerService(services.spkr)

    // Percolation manager used to replace database triggers. It modifies the store based on certain events.
    // See the 'SpkrPercolator' class for more details.
    info("Creating percolator...")
    services.spnlPersistence = new ZookeeperTaskPersistenceFactory(services.zookeeper)
    services.percolator = new SpkrPercolator(services.mry, services.scnClient, services.spnlPersistence)

    // Return the created services object
    services
  }
}
