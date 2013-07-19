package com.wajam.spk.mry.model

/**
 * Message model definition.
 */

object Message extends Model {
  // Key 1 (from parent table member) this will ensure that all the data linked to a single member is sharded together
  val username = "username"
  // Key 2, this will identify each message as a unique message
  val messageId = "message_id"
  // The author's display name simplifies the percolation, when the feed is aggregated.
  val displayName = "message_display_name"
  val content = "message_content"

  val id = messageId
  val definition = Map(
    messageId -> PropertyType.Number,
    username -> PropertyType.String,
    displayName -> PropertyType.String,
    content -> PropertyType.String
  )

  def defaultValues = Map[String, String] (
    username -> "",
    displayName -> "",
    content -> ""
  )
}
