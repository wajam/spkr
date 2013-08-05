package com.wajam.spkr.mry.model

/**
 * Subscriber model definition
 * Used to keep on a single node all members that are subscribed to a given member. Needed to populated feeds when a
 * message is posted
 */

object Subscriber extends Model {
  // The target member of the subscription
  val username = "username"
  // The 'source' member of the subscription.
  val subscriberUsername = "subscriber_username"
  val subscriberDisplayName = "subscriber_display_name"

  override def id = subscriberUsername
  val definition = Map(
    username -> PropertyType.String,
    subscriberUsername -> PropertyType.String,
    subscriberDisplayName -> PropertyType.String
  )

  def defaultValues = Map[String, String](
    username -> "",
    subscriberUsername -> "",
    subscriberDisplayName -> ""
  )
}
