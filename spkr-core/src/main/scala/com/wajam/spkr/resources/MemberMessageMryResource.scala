package com.wajam.spkr.resources

import com.wajam.nrv.data.{MString, InMessage}
import com.wajam.spkr.mry.MryCalls
import com.wajam.spkr.json.MryJsonConverter
import com.wajam.spkr.mry.model.Message

/**
 *
 */
class MemberMessageMryResource(mryCalls: MryCalls) extends MryResource(mryCalls) {

  // All the fields associated with a Message
  val model = Message

  /**
   * Posts a new message. No validation is performed. It is assumed that a valid message is received.
   * TODO: Messages may be vulnerable to XSS when sent back to the client.
   */
  override def create(request: InMessage) {
    info("Received CREATE request on member_messages resource..." + request.toString)
    val subscription = convertJsonValue(getJsonBody(request), model)
    request.parameters.get("username") match {
      case (Some(MString(username))) => {
        mryCalls.insertMessage(username, request.token, model, subscription)
          .onFailure {
          case e: Exception => request.replyWithError(e)
        }
          .onSuccess {
          case value => this.respond(request, MryJsonConverter.toJson(value))
        }
      }
      case _ => {
        request.replyWithError(new IllegalArgumentException("A username and a subscription username must be specified."))
      }
    }
  }
}
