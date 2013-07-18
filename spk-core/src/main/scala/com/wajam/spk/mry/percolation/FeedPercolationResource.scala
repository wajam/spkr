package com.wajam.spk.mry.percolation

import com.wajam.spk.mry.MrySpkDatabase
import com.wajam.scn.client.ScnClient
import com.wajam.spk.mry.model.Model
import com.wajam.mry.execution.MapValue

/**
 *
 */
class FeedPercolationResource(db: MrySpkDatabase, scn: ScnClient) extends PercolationResource{

  def PercolateTaskLogic(keys: Seq[String], values: Map[String, Any]) {}

}
