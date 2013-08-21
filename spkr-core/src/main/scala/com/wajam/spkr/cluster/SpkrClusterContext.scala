package com.wajam.spkr.cluster

import com.wajam.nrv.service.{Switchboard, ActionSupportOptions}
import com.wajam.nrv.tracing.{NullTraceRecorder, Tracer}
import com.wajam.scn.ScnConfig
import com.wajam.scn.client.ScnClientConfig
import com.wajam.nrv.cluster.LocalNode
import com.wajam.nrv.protocol.{HttpProtocol, NrvProtocol}

/**
 * This object contains predefined configurations and actionSupports (which are 'contexts' for
 * communicating in nrv). In a real-world application, we would want most of these parameters to be
 * editable in the configuration file.
 */
object SpkrClusterContext {
  val clusterSupport = new ActionSupportOptions(
    switchboard = Some(new Switchboard(numExecutor = 200,maxTaskExecutorQueueSize = 50)),
    tracer = Some(new Tracer(NullTraceRecorder))
  )

  val mrySupport = new ActionSupportOptions(
    switchboard = Some(
      new Switchboard(
        name = "mry",
        numExecutor =  200,
        maxTaskExecutorQueueSize =  50,
        banExpirationDuration =  30000L,
        banThreshold = 0.40)
    )
  )

  val scnConfig = ScnConfig(
    timestampSaveAheadMs = 5000,
    timestampSaveAheadRenewalMs =  1000,
    sequenceSaveAheadSize = 1000
  )

  val scnSupport = new ActionSupportOptions(
    switchboard = Some(
      new Switchboard(
        numExecutor = 200,
        maxTaskExecutorQueueSize = 50
      )
    )
  )

  val scnClientConfig = ScnClientConfig(
    executionRateInMs = 10,
    timeoutInMs = 3000,
    numExecutor = 100
  )

  def createNrvProtocol(localNode: LocalNode) = Some(new NrvProtocol(localNode, idleConnectionTimeoutMs =  10000, maxConnectionPoolSize = 100))
  def createHttpProtocol(localNode: LocalNode) = new HttpProtocol("http_api", localNode, idleConnectionTimeoutMs = 10000, maxConnectionPoolSize = 100)

}
