package com.wajam.spk.mry.model

/**
 * Subscriber model definition
 */

object Subscriber extends Model {
  val username = "username"
  val subscriberUsername = "subscriber_username"
  val subscriberDisplayName = "subscriber_display_name"

  val id = subscriberUsername
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
