package com.wajam.spk.mry.percolation

import com.wajam.spk.mry.{MrySpkDatabaseModel, MrySpkDatabase}
import com.wajam.scn.client.ScnClient
import com.wajam.spk.mry.model.{Subscriber, Subscription}
import com.wajam.mry.execution.{OperationApi, MapValue}
import com.wajam.mry.execution.Implicits._

/**
 *
 */
class SubscriberPercolationResource(db: MrySpkDatabase, scn: ScnClient) extends PercolationResource{

  private val sourceModel = Subscription
  private val destinationModel = Subscriber


  override def PercolateTaskLogic(keys: Seq[String], values: MapValue, token: Long) {
    // The username of the member targetted by the subscription becomes the new primary key. This way, all the subscribers
    // for the given member will be sharded on a the same node. The second key, the subscriber's username, makes the record unique.
    val key1 = values.mapValue.get(sourceModel.subscriptionUsername).get
    val key2 = values.mapValue.get(sourceModel.username).get
    val percolatedSubscriber = MapValue(Map(
      destinationModel.username -> key1,
      destinationModel.subscriberUsername -> key2,
      destinationModel.subscriberDisplayName -> "unknown user" // This would require another query. It would be useful fpr some features, like showing a list of all members subscribed to a given member
    ))
    println("inserting new subscriber %s from subscription %s".format(percolatedSubscriber, values))

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
