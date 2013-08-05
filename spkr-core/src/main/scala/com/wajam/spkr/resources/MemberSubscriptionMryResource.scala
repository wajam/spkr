package com.wajam.spkr.resources

import com.wajam.nrv.data.{MString, InMessage}
import com.wajam.spkr.mry.{MrySpkDatabaseModel, MrySpkrDatabase}
import com.wajam.mry.execution._
import com.wajam.mry.execution.Implicits._
import com.wajam.scn.client.ScnClient
import com.wajam.spkr.mry.model.Subscription
import scala.Some
import com.wajam.spkr.json.MryJsonConverter

/**
 *
 */
class MemberSubscriptionMryResource(db: MrySpkrDatabase, scn: ScnClient) extends MryResource(db,scn) {

  // All the fields associated with a subscription
  val model = Subscription

  /**
   *  Gets all the subscriptions for a given member identified by his username
   */
  override def get(request: InMessage) {
    request.parameters.get("username") match {
      case (Some(MString(username))) => {
        info("Received GET request on member_subscription resource... " + request)
        db.execute(b => {
          b.returns(b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE).get(getValidatedKey(request, model.username))
            .from(MrySpkDatabaseModel.SUBSCRIPTION_TABLE).get())
        }).
          onFailure (handleFailures(request)).
          onSuccess { case Seq(ListValue(subscriptions)) => {
            //this.respond(request, JsonConverter.toJsonList(subscriptions, model))
            this.respond(request, MryJsonConverter.toJson(subscriptions))
          }
          case _ => {
            info("Error! Unexpected data format.")
            this.respondError(request,"Unexpected data format.")
          }
        }
      }
      case _ => {
        this.respondNotFound(request,"Unable to read specified username")
      }
    }
  }



  /**
   *  Creates a new subscription between two members based.
   *  The member specified as a url parameter will be subscribed to the member specified in the POST http query data.
   */
  override def create(request: InMessage) {
    info("Received CREATE request on member_subscription resource..." + request.toString)

    //TODO: validate if user already exists (maybe check in reverse lookup table "name"?)
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
            b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE).get(username.toString).from(MrySpkDatabaseModel.SUBSCRIPTION_TABLE)
          },
          callback = (value) => {
            //this.respond(request, JsonConverter.toJsonObject(value, model))
            this.respond(request, MryJsonConverter.toJson(value))
          }
        )
      }
      case _ => {
        request.replyWithError(new IllegalArgumentException("A username must be specified."))
      }
    }
  }
}
