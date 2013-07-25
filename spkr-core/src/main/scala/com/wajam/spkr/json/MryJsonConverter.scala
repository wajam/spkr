package com.wajam.spkr.json

import com.wajam.mry.execution._
import com.wajam.mry.execution.Implicits._
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import com.wajam.mry.execution.MapValue
import net.liftweb.json.JsonAST.JString
import com.wajam.mry.execution.BoolValue
import com.wajam.mry.execution.IntValue
import com.wajam.mry.execution.ListValue
import net.liftweb.json.JsonAST.JDouble
import net.liftweb.json.JsonAST.JInt
import com.wajam.mry.execution.StringValue
import com.wajam.mry.execution.DoubleValue

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
