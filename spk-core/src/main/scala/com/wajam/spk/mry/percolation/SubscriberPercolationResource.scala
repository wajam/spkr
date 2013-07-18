package com.wajam.spk.mry.percolation

import com.wajam.spk.mry.{MrySpkDatabaseModel, MrySpkDatabase}
import com.wajam.scn.client.ScnClient
import com.wajam.spk.mry.model.{Subscriber, Subscription}
import com.wajam.mry.execution.MapValue
import com.wajam.mry.execution.Implicits._

/**
 *
 */
class SubscriberPercolationResource(db: MrySpkDatabase, scn: ScnClient) extends PercolationResource{

  protected val sourceModel = Subscription
  protected val destinationModel = Subscriber


  override def PercolateTaskLogic(keys: Seq[String], values: Map[String, Any]) {

    try
    val percolatedSubscriber = MapValue(Map(
      destinationModel.username -> values.mapValue.get(sourceModel.username).get,
      destinationModel.subscriberId -> values.mapValue.get(sourceModel.subscriptionId).get,
      destinationModel.subscriberUsername -> values.mapValue.get(sourceModel.subscriptionUsername).get,
      destinationModel.subscriberDisplayName -> values.mapValue.get(sourceModel.subscriptionDisplayName).get
    ))
    println("inserting new subscriber %s from subscription %s".format(values, percolatedSubscriber))

    db.execute(t => {
      t.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.SUBSCRIBER_TABLE).set(destinationModel.subscriberId, percolatedSubscriber)
    })
  }

}
