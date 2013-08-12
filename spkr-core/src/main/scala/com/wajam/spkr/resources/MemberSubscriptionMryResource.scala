package com.wajam.spkr.resources

import com.wajam.nrv.data.{MString, InMessage}
import com.wajam.spkr.mry.MryCalls
import com.wajam.mry.execution._
import com.wajam.mry.execution.Implicits._
import com.wajam.spkr.mry.model.Subscription
import com.wajam.spkr.json.MryJsonConverter

class MemberSubscriptionMryResource(mryCalls: MryCalls) extends MryResource(mryCalls) {

  // All the fields associated with a subscription
  val model = Subscription

  /**
   * Gets all the subscriptions for a given member identified by his username
   */
  override def get(request: InMessage) {
    request.parameters.get(model.username) match {
      case (Some(MString(username))) => {
        info("Received GET request on member_subscription resource... " + request)
        val getSubscriptionFuture = mryCalls.getSubscriptionsFromUserName(getValidatedKey(request, model.username))
        getSubscriptionFuture.onFailure(handleFailures(request))
        getSubscriptionFuture.onSuccess {
          case Seq(ListValue(subscriptions)) => {
            this.respond(request, MryJsonConverter.toJson(subscriptions))
          }
          case _ => {
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


  /**
   * Creates a new subscription between two members based.
   * The member specified as a url parameter will be subscribed to the member specified in the POST http query data.
   */
  override def create(request: InMessage) {
    info("Received CREATE request on member_subscription resource..." + request.toString)

    // TODO: validate if user already exists (maybe check in reverse lookup table "name"?)
    val subscription = convertJsonValue(getJsonBody(request), model)

    (request.parameters.get(model.username), subscription(model.subscriptionUsername).toString) match {
      case (Some(MString(username)), target) => {
        val insertSubscriptionFuture = mryCalls.insertSubscription(username, target, subscription)
        insertSubscriptionFuture.onFailure {
          case e: Exception => request.replyWithError(e)
        }
        insertSubscriptionFuture.onSuccess {
          case value => this.respond(request, MryJsonConverter.toJson(value))
        }
      }
      case _ => {
        request.replyWithError(new IllegalArgumentException("A username must be specified."))
      }
    }
  }

  /**
   * Creates a new subscription between two members based.
   * The member specified as a url parameter will be subscribed to the member specified in the POST http query data.
   */
  override def delete(request: InMessage) {
    info("Received DELETE request on member_subscription resource..." + request.toString)

    val subscription = convertJsonValue(getJsonBody(request), model)
    (request.parameters.get(model.username), subscription(model.subscriptionUsername).toString) match {
      case (Some(MString(self)), target) => {
        // We start by removing the user as a subscriber (generated through percolation)
        val deleteSubscription = mryCalls.deleteSubscriber(self, target)
        deleteSubscription.onFailure(handleFailures(request))
        deleteSubscription.onSuccess({
          case _ => {
            //We then remove the member's subscription
            val deleteSubscriptionFuture = mryCalls.deleteSubscription(self, target)
            deleteSubscriptionFuture.onFailure(handleFailures(request))
            deleteSubscriptionFuture.onSuccess({
              case _ => {
                this.respondEmptySuccess(request)
                info("Successfully deleted {}'s subscription to {}", self, target)
              }
            })
          }
        })
      }
      case _ => this.respondNotFound(request, "Unable to read specified username and subscription")
    }
  }
}
