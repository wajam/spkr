package com.wajam.spkr.mry.percolation

import com.wajam.spkr.mry.MryCalls
import com.wajam.spkr.mry.model.{Subscriber, Subscription}
import com.wajam.mry.execution.MapValue
import com.wajam.mry.execution.Implicits._

/**
 * Subscriber aggregation resource via percolation: This class builds the list of subscribers that are subscribed to
 * each member, as members insert new subscriptions.
 */
class SubscriberPercolationResource(mryCalls: MryCalls) extends PercolationResource(mryCalls) {

  private val sourceModel = Subscription
  private val destinationModel = Subscriber

  /**
   * The percolation logic receives a new subscriptions and create a subscriber entry with the same data, sharding it
   * on the right shard.
   */
  override def PercolateTaskLogic(keys: Seq[String], values: MapValue, token: Long) {
    // The username of the member targeted by the subscription becomes the new primary key. This way, all the subscribers
    // for the given member will be sharded on a the same node. The second key, the subscriber's username, makes the record unique.
    val target = keys(1) // sharded by subscription target
    val subscriber = keys(0) // the source of the subscription becomes the subscriber
    val percolatedSubscriber = MapValue(Map(
        destinationModel.username -> target,
        destinationModel.subscriberUsername -> subscriber,
        destinationModel.subscriberDisplayName -> "unknown user" // This would require another query. It would be useful fpr some features, like showing a list of all members subscribed to a given member
      ))
    debug("inserting new subscriber %s from subscription %s".format(percolatedSubscriber, values))

    mryCalls.insertSubscriber(target, subscriber, percolatedSubscriber)
      .onFailure {
      case e: Exception => throw e
    }
      .onSuccess {
      case value => debug("successfully percolated this subscriber: {}", value)
    }
  }
}
