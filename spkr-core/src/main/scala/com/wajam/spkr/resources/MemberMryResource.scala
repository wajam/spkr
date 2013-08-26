package com.wajam.spkr.resources

import com.wajam.nrv.data.InMessage
import com.wajam.spkr.mry.MryCalls
import com.wajam.mry.execution.Implicits._
import com.wajam.spkr.json.MryJsonConverter
import com.wajam.spkr.mry.model.{Subscription, Member}
import com.wajam.mry.execution.MapValue

/**
 *
 */
class MemberMryResource(mryCalls: MryCalls) extends MryResource(mryCalls) {

  // All the fields associated with a member
  val model = Member

  /**
   * Gets all the info associated to a member based on a username.
   * Note: this may also be used to validate the existence of a member.
   */
  override def get(request: InMessage) {
    info("Received GET request on member resource... " + request)
    val getMemberFuture = mryCalls.getMemberFromUsername(getValidatedKey(request, model.username))
    getMemberFuture.onFailure(handleFailures(request))
    getMemberFuture.onSuccess {
      case Seq(MapValue(member), _*) => {
        this.respond(request, MryJsonConverter.toJson(member))
      }
      case _ => {
        this.respondNotFound(request, "Requested username not found")
      }
    }
  }

  /**
   * Inserts a new member in the database.
   * Note: No validation is performed. Inserting a new member using an already existing username will succeed.
   * Since updates in mry are basically new inserts (to prevent db locks), the scenario described will caused the
   * inserted member to behave as an updated version of the previously existing member with the same username.
   *
   */
  override def create(request: InMessage) {
    info("Received CREATE request on member resource..." + request.toString)

    val member = convertJsonValue(getJsonBody(request), model)
    member.get(model.username) match {
      case Some(username) => {
        val insertMemberFuture = mryCalls.insertMember(username.toString, member)
        insertMemberFuture.onFailure {
          case e: Exception => this.respondError(request, e.toString)
        }
        insertMemberFuture.onSuccess {
          case value => {
            this.respond(request, MryJsonConverter.toJson(value))

            // On member creation, we'll subscribe the member to himself
            val selfSubscription = MapValue(Map(
              Subscription.subscriptionDisplayName -> "I", //this will display "I posted: ..."
              Subscription.subscriptionUsername -> username.toString,
              Subscription.username -> username.toString
            ))

            val insertMemberFuture = mryCalls.insertSubscription(username.toString, username.toString, selfSubscription)
            insertMemberFuture.onFailure {
              case e: Exception => error("Error subscribing to self: " + e)
            }
            insertMemberFuture.onSuccess {
              case value => debug("Self subscription inserted with success: " + value)
            }

            // Note: It would be possible to manually trigger the subscriber percolation here.
            // This would reduce the delay until the data is percolated. The primary percolation scheduled in spnl would
            // act as a "fallback" if the percolation were to fail here. In any case, it is optional.
          }
        }
      }
      case None =>
        request.replyWithError(new IllegalArgumentException("A username must be specified."))
    }
  }

  /**
   * Updates a member's display name by username. For data consistency reasons, mry does not update fields. Instead in
   * inserts a new record using the same key, but with updated values. The garbage collection system will eventually
   * delete the old record.
   */
  override def update(request: InMessage) {
    info("Received UPDATE request on member resource... " + request)
    // Extract the parameter and the body from the query
    val member = convertJsonValue(getJsonBody(request), model)
    (getValidatedKey(request, model.username), member.get(model.displayName)) match {
      case (username,Some(displayName)) => {
        // First we obtain the current record from the store
        val getMemberFuture = mryCalls.getMemberFromUsername(username.toString)
        getMemberFuture.onFailure(handleFailures(request))
        getMemberFuture.onSuccess {
          case Seq(MapValue(member), _*) => {
            // Then we change the display name from the record
            val updatedMember = member.updated(model.displayName, displayName)
            // And write the result to the store
            val insertMemberFuture = mryCalls.insertMember(username.toString, updatedMember)
            insertMemberFuture.onFailure {
              case e: Exception => this.respondError(request, e.toString)
            }
            insertMemberFuture.onSuccess {
              case value => {
                this.respond(request, MryJsonConverter.toJson(member))
              }
            }
          }
          case _ => {
            this.respondNotFound(request, "Requested username not found")
          }
        }
      }
      case _ =>
        this.respondError(request, "A username and display name must be specified.")
    }
  }
}
