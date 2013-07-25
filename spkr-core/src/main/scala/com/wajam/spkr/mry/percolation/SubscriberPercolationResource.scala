package com.wajam.spkr.mry.percolation

import com.wajam.spkr.mry.{MrySpkDatabaseModel, MrySpkrDatabase}
import com.wajam.scn.client.ScnClient
import com.wajam.spkr.mry.model.{Subscriber, Subscription}
import com.wajam.mry.execution.{OperationApi, MapValue}
import com.wajam.mry.execution.Implicits._

/**
 *  Subscriber aggregation resource via percolation: This class builds the list of subscribers that are subscribed to
 *  each member, as members insert new subscriptions.
 */
class SubscriberPercolationResource(db: MrySpkrDatabase, scn: ScnClient) extends PercolationResource{

  private val sourceModel = Subscription
  private val destinationModel = Subscriber

  /**
   * The percolation logic receives a new subscriptions and create a subscriber entry with the same data, sharding it
   * on the right shard.
   */
  override def PercolateTaskLogic(keys: Seq[String], values: MapValue, token: Long) {
    // The username of the member targeted by the subscription becomes the new primary key. This way, all the subscribers
    // for the given member will be sharded on a the same node. The second key, the subscriber's username, makes the record unique.
    val key1 = values.mapValue.get(sourceModel.subscriptionUsername).get
    val key2 = values.mapValue.get(sourceModel.username).get
    val percolatedSubscriber = MapValue(Map(
      destinationModel.username -> key1,
      destinationModel.subscriberUsername -> key2,
      destinationModel.subscriberDisplayName -> "unknown user" // This would require another query. It would be useful fpr some features, like showing a list of all members subscribed to a given member
    ))
    debug("inserting new subscriber %s from subscription %s".format(percolatedSubscriber, values))

    insertWithKey(
      db = db,
      key = key2.value.toString,
      newObj = percolatedSubscriber,
      tableAccessor = (b: OperationApi) => {
        b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE).get(key1).from(MrySpkDatabaseModel.SUBSCRIBER_TABLE)
      },
      onError = (e: Exception) => { throw e },
      callback = (_) => {})
    }
}
