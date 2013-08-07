package com.wajam.spkr.resources

import com.wajam.nrv.data.{MString, InMessage}
import com.wajam.spkr.mry.{MrySpkrDatabaseModel, MrySpkrDatabase}
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
        val InsertedMessageFuture = insertWithScnSequence(
          db = db,
          scn = scn,
          token = request.token,
          sequenceName = model.id,
          keyName = model.id,
          newRecord = (subscription ++ Map(model.username -> username)),
          tableAccessor = (b: OperationApi) => {
            b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(username.toString).from(MrySpkrDatabaseModel.POST_MESSAGE_TABLE)
          }
        )
        InsertedMessageFuture onFailure {
          case e: Exception => request.replyWithError(e)
        }
        InsertedMessageFuture onSuccess {
          case value => this.respond(request, MryJsonConverter.toJson(value))
        }
      }
      case _ => {
        request.replyWithError(new IllegalArgumentException("A username and a subscription username must be specified."))
      }
    }
  }
}
