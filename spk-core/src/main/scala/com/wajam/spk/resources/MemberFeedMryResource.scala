package com.wajam.spk.resources

import com.wajam.nrv.data.{MString, InMessage}
import com.wajam.spk.mry.{MrySpkDatabaseModel, MrySpkDatabase}
import com.wajam.mry.execution._
import com.wajam.mry.execution.Implicits._
import com.wajam.scn.client.ScnClient
import com.wajam.spk.json.JsonConverter
import com.wajam.spk.mry.model.Feed
import scala.Some

/**
 *
 */
class MemberFeedMryResource(db: MrySpkDatabase, scn: ScnClient) extends MryResource(db,scn) {

  // All the fields associated with a member
  val model = Feed

  /**
   *  Gets all the message for a given member's feed, identified by the member's username
   */
  override def get(request: InMessage) {
    request.parameters.get("username") match {
      case (Some(MString(username))) => {
        println("Received GET request on member_feed resource... " + request)
        db.execute(b => {
          b.returns(b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE).get(getValidatedKey(request, model.username))
            .from(MrySpkDatabaseModel.FEED_MESSAGE_TABLE).get())
        }).
          onFailure (handleFailures(request)).
          onSuccess { case Seq(ListValue(subscriptions)) => {
            this.respond(request, JsonConverter.toJsonList(subscriptions, model))
          }
          case a => {
            println("Error! Unexpected data format.")
            this.respondError(request,"Unexpected data format.")
          }
        }
      }
      case _ => {
        this.respondNotFound(request,"Unable to read specified username")
      }
    }
  }
}
