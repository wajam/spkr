package com.wajam.spkr.resources

import com.wajam.nrv.data.InMessage
import com.wajam.spkr.mry.{MrySpkDatabaseModel, MrySpkrDatabase}
import com.wajam.mry.execution._
import com.wajam.mry.execution.Implicits._
import com.wajam.scn.client.ScnClient
import com.wajam.spkr.json.MryJsonConverter
import com.wajam.spkr.mry.model.Member
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
      b.returns(b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE)
        .get(getValidatedKey(request, model.username)))
    }).
      onFailure (handleFailures(request)).
      onSuccess {
      case Seq(MapValue(member), _*) => {
        //this.respond(request, JsonConverter.toJsonObject(member, model))
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
   *  inserted member to behave as an updated version of the previously exising member with the same username.
   *
   */
  override def create(request: InMessage) {
    info("Received CREATE request on member resource..." + request.toString)

    val member = convertJsonValue(getJsonBody(request), model)
    member.get(model.username) match {
      case Some(username) => {
        insertWithKey(db, username.toString, member,
          tableAccessor = (b: OperationApi) => {
            b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE)
          },
          onError = (e: Exception) => {request.replyWithError(e)},
          callback = (value) => {
            //this.respond(request, JsonConverter.toJsonObject(value, model))
            this.respond(request, MryJsonConverter.toJson(value))
            // Note: It would be possible to manually trigger the appropriate percolation here.
            // This would reduce the delay until the data is percolated. The primary percolation scheduled in spnl would
            // act as a "fallback" if the percolation were to fail here. In any case, it is not necessary.
          }
        )
      }
      case None =>
        request.replyWithError(new IllegalArgumentException("A username must be specified."))
    }
  }
}
