package com.wajam.spkr.cluster

import com.wajam.scn.client.ScnClient
import com.wajam.spnl.{TaskPersistenceFactory, Spnl}
import com.wajam.nrv.cluster.{LocalNode, Cluster}
import com.wajam.spkr.config.SpkrConfig
import com.wajam.spkr.mry.percolation.SpkrPercolator
import com.wajam.scn.Scn
import com.wajam.spkr.mry.{MrySpkrDatabase, SpkrService}
import com.wajam.nrv.zookeeper.ZookeeperClient

class Services {
  // Spkr config
  var config: SpkrConfig = null

  // Nrv Components
  var localNode: LocalNode = null
  var cluster: Cluster = null

  // Scn components
  var scnClient: ScnClient = null

  // Services
  var scn: Scn = null
  var mry: MrySpkrDatabase = null
  var spkr: SpkrService = null

  // Spnl and percolation
  var spnl: Spnl = null
  var spnlPersistence: TaskPersistenceFactory = null
  var percolator: SpkrPercolator = null

  // Dynamic cluster
  var zookeeper: ZookeeperClient = null
}
