package com.wajam.spkr.mry

import com.wajam.nrv.service._
import com.wajam.nrv.protocol.Protocol
import com.wajam.nrv.data.InMessage
import com.wajam.spkr.resources._
import com.wajam.scn.client.ScnClient
import com.wajam.nrv.utils.{SynchronizedIdGenerator, TimestampIdGenerator}
import com.wajam.nrv.utils.timestamp.Timestamp
import com.wajam.nrv.Logging

/**
 * Concrete service implementation for the http_api service. This service handles http requests through a REST API by
 * calling the mry database service and converting the result to JSon. The result is sent back to the original caller.
 */
class SpkrService(name: String, database: MrySpkrDatabase, protocol: Protocol, scn: ScnClient)
  extends Service(name, new ActionSupportOptions(protocol = Some(protocol),
    resolver = Some(new Resolver(tokenExtractor = Resolver.TOKEN_RANDOM())))) with Logging {

  // Timestamp generator, required by the mry database
  val generator = new TimestampIdGenerator with SynchronizedIdGenerator[Long]

  // Resources define the exact behavior of evey possible http call
  val memberResource = new MemberMryResource(database,scn)
  val subscriptionResource = new MemberSubscriptionMryResource(database,scn)
  val postMessageResource = new MemberMessageMryResource(database,scn)
  val feedMessageResource = new MemberFeedMryResource(database,scn)

  // Each possible http path is mapped with a unique Action, which calls the appropriate resource behavior.
  registerAction(new Action(SpkrService.memberWithId, handleException(memberResource.get), ActionMethod.GET))
  registerAction(new Action(SpkrService.member, handleException(memberResource.create), ActionMethod.POST))
  registerAction(new Action(SpkrService.member, handleException(memberResource.get), ActionMethod.GET))
  registerAction(new Action(SpkrService.memberSubscription, handleException(subscriptionResource.create), ActionMethod.POST))
  registerAction(new Action(SpkrService.memberSubscription, handleException(subscriptionResource.get), ActionMethod.GET))
  registerAction(new Action(SpkrService.memberPostMessage, handleException(postMessageResource.create), ActionMethod.POST))
  registerAction(new Action(SpkrService.memberFeedMessage, handleException(feedMessageResource.get), ActionMethod.GET))

  // A method used to wrap the resource behavior with error handling.
  private def handleException(handler: InMessage => Unit)(msg: InMessage): Unit = {
    msg:InMessage => {
      try {
        handler(msg)
      } catch  {
        case e:Exception =>
          msg.replyWithError(e, Map(), ResponseHeader.RESPONSE_HEADERS, Map("error" -> "Other error: %s".format(e.toString)))
          error("Error! unable to handle API call. " + e.toString)
      }
    }
  }

  // A simple action to test the service. It does not depend on mry, and can be called using a url like localhost:port/status
  registerAction(new Action(SpkrService.test, message => {
    info("Received GET request on status...")
    message.reply(Map(), ResponseHeader.RESPONSE_HEADERS, Map("status" -> "OK"), 200)
  },ActionMethod.GET))

  /**
   * This method will register the action in addition to registering a special action that will handle the OPTION method call.
   * This is needed to support most browsers, which use the CORS mechanism (Cross-origin resource sharing).
   */
  override def registerAction(action: Action) = {
    super.registerAction(
      new Action(action.path, request => {
        request.reply(Map(), ResponseHeader.RESPONSE_HEADERS, Map(), 200)
      }, new ActionMethod("OPTIONS"))
    )
    // Insert a timestamp in the message before calling the appropriate action implementation.
    super.registerAction(
      new Action(action.path, request => {
        request.timestamp = Some(Timestamp(generator.currentTime))
        action.implementation(request)
      }, action.method)
    )
  }
}

object SpkrService {
  // Here are all the actions supported by the HTTP REST API for each and every ressources:
  val test = "/status"
  val member = "/members"
  val memberWithId = "/members/:username"
  val memberSubscription = "/members/:username/subscriptions"
  val memberSubscriber = "/members/:username/subscribers"
  val memberFeedMessage = "/members/:username/feeds"
  val memberPostMessage = "/members/:username/messages"
  val nameWithName = "/names/:display_name"
}
