package com.wajam.spk.mry.percolation

import com.wajam.spk.mry.{MrySpkDatabaseModel, MrySpkDatabase}
import com.wajam.scn.client.ScnClient
import com.wajam.spk.mry.model._
import com.wajam.mry.execution.{StringValue, ListValue, OperationApi, MapValue}
import com.wajam.mry.execution.Implicits._

/**
 *
 */
class FeedPercolationResource(db: MrySpkDatabase, scn: ScnClient) extends PercolationResource{

  private val sourceModel = Message
  private val destinationModel = Feed
  private val subscriberModel = Subscriber


  /**
   *
   */
  override def PercolateTaskLogic(keys: Seq[String], values: MapValue, token: Long) {
    // We need to get the subscribers of the new message's author, so we can add it to their feeds.
    println("feed percolation with this message: " + values)
    values.mapValue.get(sourceModel.username) match {
      case Some(StringValue(username)) => {
        db.execute(b => {
          b.returns(b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE).get(username)
            .from(MrySpkDatabaseModel.SUBSCRIBER_TABLE).get())
        }).
          onSuccess { case Seq(ListValue(subscribers)) => {
            println("these are all the subscribers that were returned = " + subscribers)
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
                    callback = (value) => { println("sucessfully percolated this: %s".format(value)) }
                  )
                }
                case a => println("Percolation failed, expected MapValue, but got this instead: " + a)//print error msg, unknown content
              }
            })
          }}
      }
      case _ => {
        println("Error percolating! Unable to read specified username.")
      }
  }
  }
}