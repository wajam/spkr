package com.wajam.spk.mry.percolation

import com.wajam.spk.resources.DatabaseHelper
import com.wajam.mry.execution.MapValue
import com.wajam.spk.mry.model.Model

/**
 * Abstract definition of a resource used for percolation
 */
abstract class PercolationResource  extends DatabaseHelper {

  /**
   * This method receives the data from the percolation table feeder and executes some percolation logic with it.
   */
  def PercolateTaskLogic(keys: Seq[String], values: Map[String, Any])

}
