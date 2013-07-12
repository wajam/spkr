package com.wajam.spk.json

import com.wajam.mry.execution.{StringValue, ListValue, MapValue, Value}
import net.liftweb.json.JsonAST._
import com.wajam.spk.mry.model.{PropertyType, Model}

/**
 * Converts data originating from mry to standard JSon.
 */
object JsonConverter {

  def toJsonObject(mryValue: Value, model: Model, fieldsFilter: Option[Seq[String]] = None): JObject = {
    mryValue match {
      case MapValue(mapVal) => {

        def getDiscriminatedValue: Option[String] = {
          model.discriminator flatMap (d => mapVal.get(d) map (_.toString))
        }

        val discriminatedValue = getDiscriminatedValue

        def isFieldFiltered(k: String) = fieldsFilter match {
          case None => true
          case Some(ff) => ff.contains(k)
        }

        def isFieldFound(k: String) = {
          mapVal.contains(k) || model.defaultValues.contains(k)
        }

        def isFieldDiscriminated(k: String) = discriminatedValue match {
          case Some(dv) if model.filteredFields.contains(k) =>
            model.filteredFields(k).contains(dv)
          case _ => true
        }

        val fields = for {
          (k, kType) <- model.definitionAsSeq
          if (isFieldFiltered(k) && isFieldFound(k) && isFieldDiscriminated(k))
        } yield {
          kType match {
            case PropertyType.Number => JField(k, JInt(asValue(mapVal, k, model).toLong))
            case PropertyType.Float => JField(k, JDouble(asValue(mapVal, k, model).toDouble))
            case PropertyType.Bool => JField(k, JBool(Seq("1", "true").contains(asValue(mapVal, k, model))))
            case PropertyType.MapList => JField(k, asListOfMap(mapVal, k))
            case PropertyType.StringList => JField(k, asListOfString(mapVal, k))
            case PropertyType.String | PropertyType.Date => JField(k, JString(asValue(mapVal, k, model)))
            case _ => throw new RuntimeException("Invalid JsonType")
          }
        }

        JObject(fields.toList)
      }
      case _ => null
    }
  }

  private def asValue(mapVal: Map[String, Value], k: String, model: Model) = {
    mapVal.get(k) match {
      case Some(v) => v.toString
      case _ => model.defaultValues(k)
    }
  }

  private def asListOfMap(mapVal: Map[String, Value], k: String): JArray = {
    mapVal.get(k) match {
      case Some(ListValue(list)) => JArray(list.map({
        case MapValue(mv) => JObject(mv.map({
          case (name, value) => (JField(name, JString(value.toString)))
        }).toList)
      }).toList)
      case _ => JArray(Nil)
    }
  }

  private def asListOfString(mapVal: Map[String, Value], k: String): JArray = {
    mapVal.get(k) match {
      case Some(ListValue(lv)) => JArray(lv.map({
        case StringValue(str) => JString(str)
      }).toList)
      case _ => JArray(Nil)
    }
  }

  def toJsonList(mryList: Seq[Value], model: Model, fieldsFilter: Option[Seq[String]] = None): Seq[JObject] = {
    mryList map {
      value => toJsonObject(value, model, fieldsFilter)
    }
  }
}