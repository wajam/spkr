package com.wajam.spk.resources

import com.wajam.nrv.data.{MString, InMessage}
import com.wajam.spk.mry.{MrySpkDatabaseModel, MrySpkDatabase}
import com.wajam.mry.execution._
import com.wajam.mry.execution.Implicits._
import com.wajam.scn.client.ScnClient
import com.wajam.spk.json.JsonConverter
import com.wajam.spk.mry.model.Message

/**
 *
 */
class MemberMessageMryResource(db: MrySpkDatabase, scn: ScnClient) extends MryResource(db,scn) {

  // All the fields associated with a Message
  val model = Message

  /**
   * Posts a new message. No validation is performed. It is assumed that a valid message is received.
   * Messages may be exposed XSS issues when sent back to the client.
   */
  override def create(request: InMessage) {
    println("Received CREATE request on member_messages resource..." + request.toString)
    val subscription = convertJsonValue(getJsonBody(request), model)

    request.parameters.get("username") match {
      case (Some(MString(username))) => {
        insertWithScnSequence(db, scn,  request, model.id, model.id,
            (subscription ++ Map(model.username -> username)),
            tableAccessor = (b: OperationApi) => {
              b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE).get(username.toString).from(MrySpkDatabaseModel.POST_MESSAGE_TABLE)
            },
          callback = (value) => {
            this.respond(request, JsonConverter.toJsonObject(value, model))
            //TODO: percolate here (timeline)
          }
        )
      }
      case _ => {
        request.replyWithError(new IllegalArgumentException("A username and a subscription username must be specified."))
      }
    }
  }
}
