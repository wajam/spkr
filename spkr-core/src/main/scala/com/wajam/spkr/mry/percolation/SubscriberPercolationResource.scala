package com.wajam.spkr.mry.percolation

import com.wajam.spkr.mry.{MrySpkrDatabaseModel, MrySpkrDatabase}
import com.wajam.scn.client.ScnClient
import com.wajam.spkr.mry.model.{Subscriber, Subscription}
import com.wajam.mry.execution.{OperationApi, MapValue}
import com.wajam.mry.execution.Implicits._

/**
 *  Subscriber aggregation resource via percolation: This class builds the list of subscribers that are subscribed to
 *  each member, as members insert new subscriptions.
 */
class SubscriberPercolationResource(db: MrySpkrDatabase, scn: ScnClient) extends PercolationResource {

  private val sourceModel = Subscription
  private val destinationModel = Subscriber

  /**
   * The percolation logic receives a new subscriptions and create a subscriber entry with the same data, sharding it
   * on the right shard.
   */
  override def PercolateTaskLogic(keys: Seq[String], values: MapValue, token: Long) {
    println("percolating new sub, source data: keys=" + keys + " | values=" + values)
    println("result: " + keys(0) + " is a subscriber of " + keys(1))
    // The username of the member targeted by the subscription becomes the new primary key. This way, all the subscribers
    // for the given member will be sharded on a the same node. The second key, the subscriber's username, makes the record unique.
    val key0 = keys(1) // sharded by subscription target
    val key1 = keys(0) // the source of the subscription becomes the subscriber
    val percolatedSubscriber = MapValue(Map(
      destinationModel.username -> key0,
      destinationModel.subscriberUsername -> key1,
      destinationModel.subscriberDisplayName -> "unknown user" // This would require another query. It would be useful fpr some features, like showing a list of all members subscribed to a given member
    ))
    debug("inserting new subscriber %s from subscription %s".format(percolatedSubscriber, values))

    val InsertedSubscriberFuture = insertWithKey(
      db = db,
      key = key1,
      newRecord = percolatedSubscriber,
      tableAccessor = (b: OperationApi) => {
        b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(key0).from(MrySpkrDatabaseModel.SUBSCRIBER_TABLE)
      })

    InsertedSubscriberFuture onFailure {
      case e: Exception => throw e
    }
    InsertedSubscriberFuture onSuccess {
      case value => debug("successfully percolated this subscriber: {}", value)
    }
  }
}
