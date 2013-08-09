package com.wajam.spkr.resources

import com.wajam.nrv.data.{MString, InMessage}
import com.wajam.spkr.mry.{MrySpkrDatabaseModel, MrySpkrDatabase}
import com.wajam.mry.execution._
import com.wajam.mry.execution.Implicits._
import com.wajam.scn.client.ScnClient
import com.wajam.spkr.json.MryJsonConverter
import com.wajam.spkr.mry.model.{Subscription, Member}
import com.wajam.mry.execution.MapValue

/**
 *
 */
class MemberMryResource(db: MrySpkrDatabase, scn: ScnClient) extends MryResource(db,scn) {

  // All the fields associated with a member
  val model = Member

  /**
   *  Gets all the info associated to a member based on a username.
   *  Note: this may also be used to validate the existence of a member.
   */
  override def get(request: InMessage) {
    info("Received GET request on member resource... " + request)
    db.execute(b => {
      b.returns(b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE)
        .get(getValidatedKey(request, model.username)))
    }).
      onFailure (handleFailures(request)).
      onSuccess {
      case Seq(MapValue(member), _*) => {
        this.respond(request, MryJsonConverter.toJson(member))
      }
      case _ => {
        this.respondNotFound(request,"Requested username not found")
      }
    }
  }

  /**
   *  Inserts a new member in the database.
   *  Note: No validation is performed. Inserting a new member using an already existing username will succeed.
   *  Since updates in mry are basically new inserts (to prevent db locks), the scenario described will caused the
   *  inserted member to behave as an updated version of the previously existing member with the same username.
   *
   */
  override def create(request: InMessage) {
    info("Received CREATE request on member resource..." + request.toString)

    val member = convertJsonValue(getJsonBody(request), model)
    member.get(model.username) match {
      case Some(username) => {
        val InsertedMemberFuture = insertWithKey(db, username.toString, member,
          tableAccessor = (b: OperationApi) => {
            b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE)
          }
        )

        InsertedMemberFuture onFailure {
          case e: Exception => request.replyWithError(e)
        }
        InsertedMemberFuture onSuccess {
          case value => {
            this.respond(request, MryJsonConverter.toJson(value))
            
            // On member creation, we'll subscribe the member to himself
            val selfSubscription = MapValue(Map(
              Subscription.subscriptionDisplayName -> "I", //this will display "I posted: ..."
              Subscription.subscriptionUsername -> username.toString,
              Subscription.username -> username.toString
            ))

            val InsertedSelfSubscriptionFuture = insertWithKey(
              db = db,
              key = username.toString,
              newRecord = (selfSubscription),
              tableAccessor = (b: OperationApi) => {
                b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(username.toString).from(MrySpkrDatabaseModel.SUBSCRIPTION_TABLE)
              }
            )

            InsertedSelfSubscriptionFuture onFailure {
              case e: Exception => error("error subscribing to self: " + e)
            }
            InsertedSelfSubscriptionFuture onSuccess {
              case value =>  debug("self sub inserted with success: " + value)
            }
            
            // Note: It would be possible to manually trigger the appropriate percolation here.
            // This would reduce the delay until the data is percolated. The primary percolation scheduled in spnl would
            // act as a "fallback" if the percolation were to fail here. In any case, it is optional.
          }
        }
      }
      case None =>
        request.replyWithError(new IllegalArgumentException("A username must be specified."))
    }
  }
}
