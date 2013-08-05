package com.wajam.spkr.mry.model

import java.text.SimpleDateFormat
import java.util.Calendar
import net.liftweb.json.JsonAST.{JField, JObject}

/**
 * Base model definition
 */

abstract class Model {
  def id: String

  def definition: Map[String, PropertyType.PropertyType]

  lazy val definitionAsSeq: List[(String, PropertyType.PropertyType)] = definition.toList

  def name = this.getClass.getName

  def updateableColumns = definition.keys.filter(!unmodifiableColumns.contains(_)).toSeq

  def defaultValues: Map[String, String]

  /**
   * Values that are accepted only for inserts
   * @return
   */
  def hiddenKeys: Map[String, PropertyType.PropertyType] = Map()

  def discriminator: Option[String] = None

  def filteredFields: Map[String, Set[Any]] = Map()

  // Columns that should never be updated (in a PUT query)
  protected def unmodifiableColumns = Seq(id)

  def filterResult(result: JObject): JObject = discriminator match {
    case Some(discriminatedField) => result.values.get(discriminatedField) match {
      case Some(discriminatedValue) => JObject(result.obj filter {
        case JField(name, _) => filteredFields.get(name) match {
          case Some(allowedValues) => allowedValues.contains(discriminatedValue.toString)
          case None => true
        }
      })
      case None => result
    }
    case None => result
  }

  /**
   * Map of functions that validate if keys are valid
   * @return
   */
  def validateKeys: Map[String, (Any) => Boolean] = Map()

  def validateValues: Map[String, (Any) => Boolean] = Map()

}

object PropertyType extends Enumeration {
  type PropertyType = Value
  val Number, Float, Bool, String, MapList, StringList = Value
}

