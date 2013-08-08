package com.wajam.spkr.resources

import com.wajam.nrv.data.{MString, InMessage}
import com.wajam.spkr.mry.{MrySpkrDatabaseModel, MrySpkrDatabase}
import com.wajam.mry.execution._
import com.wajam.mry.execution.Implicits._
import com.wajam.scn.client.ScnClient
import com.wajam.spkr.mry.model.{Subscriber, Subscription}
import com.wajam.spkr.json.MryJsonConverter

class MemberSubscriptionMryResource(db: MrySpkrDatabase, scn: ScnClient) extends MryResource(db,scn) {

  // All the fields associated with a subscription
  val model = Subscription

  /**
   *  Gets all the subscriptions for a given member identified by his username
   */
  override def get(request: InMessage) {
    request.parameters.get(model.username) match {
      case (Some(MString(username))) => {
        info("Received GET request on member_subscription resource... " + request)
        db.execute(b => {
          b.returns(b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(getValidatedKey(request, model.username))
            .from(MrySpkrDatabaseModel.SUBSCRIPTION_TABLE).get())
        }).
          onFailure (handleFailures(request)).
          onSuccess { case Seq(ListValue(subscriptions)) => {
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

    request.parameters.get(model.username) match {
      case (Some(MString(username))) => {
        val InsertedSubscriptionFuture = insertWithScnSequence(
          db = db,
          scn = scn,
          token = request.token,
          sequenceName = model.id,
          keyName = model.id,
          newRecord = (subscription ++ Map(model.username -> username)),
          tableAccessor = (b: OperationApi) => {
            b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(username.toString).from(MrySpkrDatabaseModel.SUBSCRIPTION_TABLE)
          }
        )

        InsertedSubscriptionFuture onFailure {
          case e: Exception => request.replyWithError(e)
        }
        InsertedSubscriptionFuture onSuccess {
          case value => this.respond(request, MryJsonConverter.toJson(value))
        }
      }
      case _ => {
        request.replyWithError(new IllegalArgumentException("A username must be specified."))
      }
    }
  }

  /**
   *  Creates a new subscription between two members based.
   *  The member specified as a url parameter will be subscribed to the member specified in the POST http query data.
   */
  override def delete(request: InMessage) {
    info("Received DELETE request on member_subscription resource..." + request.toString)

    val subscription = convertJsonValue(getJsonBody(request), model)
    (request.parameters.get(model.username),
      subscription(model.subscriptionUsername),
      subscription(model.subscriptionId)) match {
        case (Some(MString(self)), target, subscriptionId) => {
          // We start by removing the user as a subscriber (generated through percolation)
          db.execute(b => {
            b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(target.toString).
              from(MrySpkrDatabaseModel.SUBSCRIBER_TABLE).delete(self)
          }).onFailure (handleFailures(request))
          .onSuccess( { case _=> {
              //We then remove the member's subscription
              db.execute(b => {
                b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(self).
                  from(MrySpkrDatabaseModel.SUBSCRIPTION_TABLE).delete(subscriptionId.toString)
              }).onFailure (handleFailures(request))
                .onSuccess({
                case _ => {
                  this.respondEmptySuccess(request)
                  info("Successfully deleted {}'s subscription to {}",self, target)
                }
              })
            }
          })
        }
        case _ => this.respondNotFound(request,"Unable to read specified username and subscription")
      }
  }
}
