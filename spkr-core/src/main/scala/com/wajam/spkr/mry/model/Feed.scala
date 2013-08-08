package com.wajam.spkr.mry.model

/**
 * Feed model definition.
 * The feed contains all the message that a member should be able to read.
 */

object Feed extends Model {
  // Key 1 from parent table, allows sharding on same node. Refers to the member that reads the messages on his feed.
  val username = "username"
  // Key 2, a unique identifier for each
  val feedId = "feed_id"
  // The id of the member who wrote the message
  val subscriptionUsername = "subscription_username"
  // The name that should be displayed next to the message
  val subscriptionDisplayName = "subscription_display_name"
  // Message content is duplicated on the local shard (same shard thanks to the username key).
  val content = "message_content"

  override def id = feedId
  val definition = Map(
    username -> PropertyType.String,
    feedId -> PropertyType.Number,
    subscriptionUsername -> PropertyType.String,
    subscriptionDisplayName -> PropertyType.String,
    content -> PropertyType.String
  )

  def defaultValues = Map[String, String](
    username -> "",
    subscriptionUsername -> "",
    subscriptionDisplayName -> "",
    content -> ""
  )
}