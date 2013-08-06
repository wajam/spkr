package com.wajam.spkr.resources

import com.wajam.nrv.data.{MString, InMessage}
import com.wajam.spkr.mry.{MrySpkDatabaseModel, MrySpkrDatabase}
import com.wajam.mry.execution._
import com.wajam.mry.execution.Implicits._
import com.wajam.scn.client.ScnClient
import com.wajam.spkr.json.MryJsonConverter
import com.wajam.spkr.mry.model.Message

/**
 *
 */
class MemberMessageMryResource(db: MrySpkrDatabase, scn: ScnClient) extends MryResource(db,scn) {

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
        insertWithScnSequence(
          db = db,
          scn = scn,
          token = request.token,
          onError = (e: Exception) => {request.replyWithError(e)},
          sequenceName = model.id,
          keyName = model.id,
          newRecord = (subscription ++ Map(model.username -> username)),
          tableAccessor = (b: OperationApi) => {
            b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE).get(username.toString).from(MrySpkDatabaseModel.POST_MESSAGE_TABLE)
          },
          callback = (value) => {
            this.respond(request, MryJsonConverter.toJson(value))
          }
        )
      }
      case _ => {
        request.replyWithError(new IllegalArgumentException("A username and a subscription username must be specified."))
      }
    }
  }
}
