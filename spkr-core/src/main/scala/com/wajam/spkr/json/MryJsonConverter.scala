package com.wajam.spkr.json

import com.wajam.mry.execution.Implicits._
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import com.wajam.mry.execution._

object MryJsonConverter {
  def toJson(value: Value): JValue = {
    value match {
      case MapValue(mv) => mv.map {
        case (k, v) => k -> toJson(v)
      }

      case ListValue(v) => v.map(toJson)

      case StringValue(s) => s

      case IntValue(i) => i

      case DoubleValue(d) => d

      case BoolValue(b) => b

      case NullValue => JNull
    }
  }

  def fromJson(jvalue: JValue): Value = {
    jvalue match {
      case JString(v) => v

      case JInt(v) => v.toLong

      case JDouble(v) => v

      case JBool(v) => v

      case JArray(l) => l.map(fromJson)

      case JObject(o) => o.obj.map(field => field.name -> fromJson(field.value)).toMap

      case _ => NullValue

    }
  }
}
