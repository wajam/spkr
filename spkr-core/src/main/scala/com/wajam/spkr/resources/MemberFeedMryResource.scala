package com.wajam.spkr.resources

import com.wajam.nrv.data.InMessage
import com.wajam.spkr.mry.MryCalls
import com.wajam.mry.execution.Implicits._
import com.wajam.spkr.mry.model.Feed
import com.wajam.nrv.data.MString
import com.wajam.mry.execution.ListValue
import com.wajam.spkr.json.MryJsonConverter

/**
 * Resource for feeds. A feed is the set of messages a given user wants to read based on his subscription.
 */
class MemberFeedMryResource(mryCalls: MryCalls) extends MryResource(mryCalls) {

  // All the fields associated with a member
  val model = Feed

  /**
   * Gets all the messages for a given member's feed, identified by the member's username
   */
  override def get(request: InMessage) {
    request.parameters.get("username") match {
      case (Some(MString(username))) => {
        info("Received GET request on member_feed resource... " + request)
        val getFeedFuture =mryCalls.getFeedFromUsername(getValidatedKey(request, model.username))
        getFeedFuture.onFailure(handleFailures(request))
        getFeedFuture.onSuccess {
          case Seq(ListValue(feedMessages)) => {
            this.respond(request, MryJsonConverter.toJson(feedMessages))
          }
          case a => {
            info("Error! Unexpected data format.")
            this.respondError(request, "Unexpected data format.")
          }
        }
      }
      case _ => {
        this.respondNotFound(request, "Unable to read specified username")
      }
    }
  }
}
