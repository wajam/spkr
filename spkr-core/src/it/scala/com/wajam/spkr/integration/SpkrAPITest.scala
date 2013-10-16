package com.wajam.spkr.integration

import com.wajam.nrv.extension.json.integration.JsonHttpClientOperations
import com.wajam.spkr.mry.model.{Message, Member, Subscription}
import com.wajam.spkr.mry.SpkrService
import com.wajam.spkr.json.MryJsonConverter
import com.wajam.mry.execution.{MapValue, ListValue}

/**
 * Generates ready-to-insert test records for all MryResource Tests.
 */
trait SpkrAPITest extends JsonHttpClientOperations {

  def generateMember: Map[String, Any] = Map(Member.username -> generateRandomString, Member.displayName -> generateRandomString)

  def generateSubscription(memberSubscribing: Map[String, Any], memberTargeted: Map[String, Any]) = {
    Map(
      Subscription.username -> memberSubscribing(Member.username).toString,
      Subscription.subscriptionUsername -> memberTargeted(Member.username).toString,
      Subscription.subscriptionDisplayName -> memberTargeted(Member.displayName).toString
    )
  }

  def generateMessage(authorUsername: String, authorDisplayName: String): Map[String, Any] =
  {
    Map(
      Message.username -> authorUsername,
      Message.displayName -> authorDisplayName,
      Message.messageId -> util.Random.nextInt(Int.MaxValue),
      Message.content -> generateRandomString()
    )
  }

  private def generateRandomString(): String = {
    val characters = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
    ((1 to 10) map (_ => characters(util.Random.nextInt(characters.length)))).mkString
  }


  def insertMember(record: Map[String, Any]) = {
    post(SpkrService.member, record)
  }

  def getMember(key: String) = {
    get(SpkrService.memberWithId.replaceFirst(":" + Member.id, key))
  }

  def insertSubscription(memberKey: String, record: Map[String, Any]) = {
    post(SpkrService.memberSubscription.replaceFirst(":" + Member.id, memberKey), record)
  }

  def getSubscription(key: String) = {
    get(SpkrService.memberSubscription.replaceFirst(":" + Member.id, key))
  }

  def deleteSubscription(member: String, target: String) = {
    delete(SpkrService.memberSubscriptionWithTarget
      .replaceFirst(":" + Member.id, member)
      .replaceFirst(":" + Subscription.subscriptionUsername, target)
    )
  }

  def insertMessage(memberKey: String, record: Map[String, Any]) = {
    post(SpkrService.memberPostMessage.replaceFirst(":" + Member.id, memberKey), record)
  }

  def getFeed(key: String) = {
    get(SpkrService.memberFeedMessage.replaceFirst(":" + Member.id, key))
  }

  def extractList(data: ResponseData) = {
    MryJsonConverter.fromJson(data) match {
      case ListValue(subscriptionsList) => subscriptionsList.map(_ match { case MapValue(m) => m})
      case _ => throw new Exception("Unexpected subscription format.")
    }
  }
}
