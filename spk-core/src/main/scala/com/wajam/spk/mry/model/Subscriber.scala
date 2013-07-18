package com.wajam.spk.mry.model

/**
 * Subscriber model definition
 */

object Subscriber extends Model {
  val username = "username"
  val subscriberId = "subscriber_id"
  val subscriberUsername = "subscriber_username"
  val subscriberDisplayName = "subscriber_display_name"

  val id = subscriberId
  val definition = Map(
    subscriberId -> PropertyType.Number,
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
