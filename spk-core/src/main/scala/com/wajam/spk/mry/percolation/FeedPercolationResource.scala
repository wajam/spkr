package com.wajam.spk.mry.percolation

import com.wajam.spk.mry.{MrySpkDatabaseModel, MrySpkDatabase}
import com.wajam.scn.client.ScnClient
import com.wajam.spk.mry.model._
import com.wajam.mry.execution.{StringValue, ListValue, OperationApi, MapValue}
import com.wajam.mry.execution.Implicits._
import com.wajam.nrv.Logging

/**
 *  Feed aggregation resource via percolation: This class builds the list of messages that are displayed on each user's
 *  feed as they are posted by other members..
 */
class FeedPercolationResource(db: MrySpkDatabase, scn: ScnClient) extends PercolationResource with Logging {

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
    info("feed percolation with this message: " + values)
    values.mapValue.get(sourceModel.username) match {
      case Some(StringValue(username)) => {
        db.execute(b => {
          b.returns(b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE).get(username)
            .from(MrySpkDatabaseModel.SUBSCRIBER_TABLE).get())
        }).
          onSuccess { case Seq(ListValue(subscribers)) => {
            info("these are all the subscribers that were returned = " + subscribers)
            subscribers.foreach({
              _ match {
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
                  // Insert new feed entry
                  insertWithScnSequence(
                    db = db,
                    scn = scn,
                    token = token,
                    onError = (e: Exception) => { throw e }, // If the percolation action throws an error, spnl will automaticaly scedule a retry.
                    sequenceName = destinationModel.id,
                    keyName = destinationModel.id,
                    newRecord = percolatedFeedMessage,
                    tableAccessor = (b: OperationApi) => {
                      b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE).get(key1).from(MrySpkDatabaseModel.FEED_MESSAGE_TABLE)
                    },
                    callback = (value) => { debug("successfully percolated this: %s".format(value)) }
                  )
                }
                case a => info("Percolation failed, expected MapValue, but got this instead: " + a)//print error msg, unknown content
              }
            })
          }}
      }
      case _ => {
        error("Error percolating! Unable to read specified username.")
      }
  }
  }
}
