package com.wajam.spk.mry.model


/**
 * Member model definition
 */

object Member extends Model {
  // unique Id
  val username = "username"
  // TODO: support display name update
  val displayName = "display_name"

  val id = username
  val definition = Map(
    username -> PropertyType.String,
    displayName -> PropertyType.String
  )

  def defaultValues = Map[String, String](
    username -> "",
    displayName -> "new_user" //todo: use this when display name update is supported
  )
}
