package com.wajam.spk.mry.model


/**
 * Member model definition
 */

object Member extends Model {
  val username = "username"
  val displayName = "display_name"

  val id = username
  val definition = Map(
    username -> PropertyType.String,
    displayName -> PropertyType.String
  )

  def defaultValues = Map[String, String](
    username -> "",
    displayName -> "new_user"
  )
}
