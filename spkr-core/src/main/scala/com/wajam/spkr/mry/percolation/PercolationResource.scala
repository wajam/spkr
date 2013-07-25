package com.wajam.spkr.mry.percolation

import com.wajam.spkr.resources.DatabaseHelper
import com.wajam.mry.execution.MapValue
import com.wajam.nrv.Logging

/**
 * Abstract definition of a resource used for percolation
 */
abstract class PercolationResource  extends DatabaseHelper with Logging {

  /**
   * This method receives the data from the percolation table feeder and executes some percolation logic with it.
   */
  def PercolateTaskLogic(keys: Seq[String], values: MapValue, token: Long)

}
