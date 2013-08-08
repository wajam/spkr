package com.wajam.spkr.cluster

import com.wajam.spkr.config.SpkrConfig
import com.wajam.nrv.Logging
import com.wajam.nrv.cluster.{LocalNode, Cluster, StaticClusterManager}
import com.wajam.spkr.mry.{SpkrService, MrySpkrDatabase}
import com.wajam.scn.Scn
import com.wajam.scn.storage.StorageType
import com.wajam.scn.client.ScnClient
import com.wajam.nrv.extension.json.codec.JsonCodec
import com.wajam.spkr.mry.percolation.SpkrPercolator
import com.wajam.spnl.NoTaskPersistenceFactory

/**
 * A static cluster doesn't use Zookeeper for cluster wide coordination. It does not save any contextual state info, like
 * percolation position and SCN sequence which will be reinitialized after a server restart. It assumes all nodes are
 * always up and running. However, since it doesn't depend on zookeeper, it's an excellent option for testing services
 * on a simple cluster.
 */
object StaticSpkrClusterCreator extends Logging  {

  def buildStaticCluster(config: SpkrConfig): Services = {
    // 'Services' contains everything we need to run our Spkr server
    val services: Services = new Services
    services.config = config
    services.localNode = new LocalNode(config.getSpkListenAddress, Map("nrv" -> config.getNrvListenPort, "http_api" -> config.getSpkListenPort))
    info("Local node is %s".format(services.localNode))

    // The "cluster manager" manages each node's memberships to multiple services.
    info("Creating static cluster manager...")
    val clusterManager = new StaticClusterManager

    // The cluster manages all services and nodes
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
    clusterManager.addMembers(services.mry, config.getStaticClusterMryMembers) //only necessary with static manager

    // Service #2: SCN Service
    // SCN client (sequence number generator framework) for Id and timestamp generation.
    info("Creating scn client and service...")
    services.scn = new Scn(serviceName = "scn", SpkrClusterContext.scnConfig, storageType = StorageType.MEMORY, None)
    services.scn.applySupportOptions(SpkrClusterContext.scnSupport)
    services.scnClient = new ScnClient(services.scn, SpkrClusterContext.scnClientConfig)
    services.cluster.registerService(services.scn)
    clusterManager.addMembers(services.scn, config.getStaticClusterScnMembers)

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
    services.spnlPersistence = new NoTaskPersistenceFactory
    services.percolator = new SpkrPercolator(services.mry, services.scnClient, services.spnlPersistence)

    // Return the created services object
    services
  }
}
