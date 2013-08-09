package com.wajam.spkr.mry.percolation

import com.wajam.spkr.mry.{MrySpkrDatabaseModel, MrySpkrDatabase}
import com.wajam.scn.client.ScnClient
import com.wajam.spkr.mry.model._
import com.wajam.mry.execution.{StringValue, ListValue, OperationApi, MapValue}
import com.wajam.mry.execution.Implicits._
import com.wajam.nrv.Logging

/**
 *  Feed aggregation resource via percolation: This class builds the list of messages that are displayed on each user's
 *  feed as they are posted by other members..
 */
class FeedPercolationResource(db: MrySpkrDatabase, scn: ScnClient) extends PercolationResource with Logging {

  private val sourceModel = Message
  private val destinationModel = Feed
  private val subscriberModel = Subscriber

  /**
   *  This task is trigger when a new messages is posted. Here's what it does:
   *  1) gets the list of members subscribed to the author of the messages
   *  2) Inserts a new feed entry for each one of them, based on the newly posted message.
   */
  override def PercolateTaskLogic(keys: Seq[String], values: MapValue, token: Long) {
    // We need to get the subscribers of the new message's author, so we can add it to their feeds.
    values.mapValue.get(sourceModel.username) match {
      case Some(StringValue(username)) => {
        db.execute(b => {
          b.returns(b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(username)
            .from(MrySpkrDatabaseModel.SUBSCRIBER_TABLE).get())
        }).onSuccess { case Seq(ListValue(subscribers)) => {
          subscribers.foreach({
            // Create new feed entry
            case MapValue(subscriber) => {
              val key1 = subscriber.get(subscriberModel.subscriberUsername).get
              // note: key2 is an auto-generated scn id to make every feed entry unique
              val percolatedFeedMessage = MapValue(Map(
                destinationModel.username -> key1,
                destinationModel.subscriptionUsername -> values.mapValue.get(sourceModel.username).get,
                destinationModel.subscriptionDisplayName -> values.mapValue.get(sourceModel.displayName).get,
                destinationModel.content -> values.mapValue.get(sourceModel.content).get
              ))

              debug("inserting new feed entry for member %s: %s".format(username,percolatedFeedMessage.get(destinationModel.content).get))

              val insertedFeedFuture = insertWithScnSequence(
                db = db,
                scn = scn,
                token = token,
                model = destinationModel,
                newRecord = percolatedFeedMessage) { (b: OperationApi) => {
                  b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(key1).from(MrySpkrDatabaseModel.FEED_MESSAGE_TABLE)
                }
              }

              insertedFeedFuture onFailure {
                // If the percolation action throws an error, spnl will automatically schedule a retry.
                case e: Exception => throw e
              }
              insertedFeedFuture onSuccess {
                case value => debug("successfully percolated this feed message: {}", value)
              }

            }
            case a => info("Percolation failed, expected MapValue, but got this instead: " + a) // print error msg, unknown content
          })
        }}
      }
      case _ => {
        error("Error percolating! Unable to read specified username.")
      }
  }
  }
}
