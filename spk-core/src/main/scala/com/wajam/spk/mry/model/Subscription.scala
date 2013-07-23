package com.wajam.spk.mry.model

/**
 * Subscription model definition
 * Represents a unidirectional link between two members. Subscriptions are only used to populate other member's
 * subscribers through percolation.
 */

object Subscription extends Model {
  val username = "username"
  val subscriptionId = "subscription_id"
  val subscriptionUsername = "subscription_username"
  val subscriptionDisplayName = "subscription_display_name"

  val id = subscriptionId
  val definition = Map(
    subscriptionId -> PropertyType.Number,
    username -> PropertyType.String,
    subscriptionUsername -> PropertyType.String,
    subscriptionDisplayName -> PropertyType.String
  )

  def defaultValues = Map[String, String](
    username -> "",
    subscriptionUsername -> "",
    subscriptionDisplayName -> ""
  )
}
