package com.wajam.spkr.resources

import com.wajam.mry.execution.{Value, Variable, OperationApi, MapValue}
import com.wajam.mry.execution.Implicits._
import com.wajam.spkr.mry.MrySpkrDatabase
import com.wajam.scn.client.ScnClient
import com.wajam.spkr.mry.model.{PropertyType, Model}
import net.liftweb.json._
import com.wajam.nrv.data.{InMessage, MList, MString}
import com.wajam.nrv.Logging
import com.wajam.nrv.utils.{Promise, Future}

/**
 * This trait offers a template for the creation of additional resources. It offers an interface to
 * handle all 5 possible query types from an HTTP REST call. The exact implementation to handle each
 * call must be defined in every concrete Resource.
 * Note: Some browsers will use OPTIONS as a query time (see CORS). In the case of Spkr, OPTIONS are handled in the
 * SpkrService.
 *
 */
abstract class MryResource(protected val db: MrySpkrDatabase, scn: ScnClient) extends MessageHelper with ParamExtractor with DatabaseHelper with JsonHelper with Logging {

  /**
   * Respond to GET resource/
   */
  def index(request: InMessage) {
    respondNotImplemented(request)
  }

  /**
   * Respond to POST resource
   */
  def create(request: InMessage) {
    respondNotImplemented(request)
  }

  /**
   * Response to GET resource/:resourceid
   */
  def get(request: InMessage) {
    respondNotImplemented(request)
  }

  /**
   * Respond to PUT resource/:resourceid
   */
  def update(request: InMessage) {
    respondNotImplemented(request)
  }

  /**
   * Respond to DELETE resource/:resourceid
   */
  def delete(request: InMessage) {
    respondNotImplemented(request)
  }
}

/**
 * Offers methods to quickly send generic messages, like error responses.
 */
trait MessageHelper {
  //The following constant will be used to reply to queries using a static header
  val RESPONSE_HEADERS = ResponseHeader.RESPONSE_HEADERS

  // Methods to respond to requests causing various types of errors.
  protected def respondNotImplemented(request: InMessage, description: String = "") {
    request.reply(Map(), RESPONSE_HEADERS, Map("error" -> "Not implemented: %s".format(description)), code = 501)
  }
  protected def respondNotFound(request: InMessage, description: String = " ") {
    request.reply(Map(), RESPONSE_HEADERS, Map("error" -> "Not Found: %s".format(description)), code = 404)
  }
  protected def respondConflict(request: InMessage, description: String = " ") {
    request.reply(Map(), RESPONSE_HEADERS, Map("error" -> "Conflict: %s".format(description)), code = 409)
  }
  protected def respondError(request: InMessage, description: String = " ") {
    request.reply(Map(), RESPONSE_HEADERS, Map("error" -> "Other error: %s".format(description)), code = 400)
  }
  protected def respondEmptySuccess(request: InMessage) {
    request.reply(Map(), RESPONSE_HEADERS, Map(), code = 200)
  }
  protected def respond(request: InMessage, data: Any, code: Int = 200) {
    request.reply(Map(), RESPONSE_HEADERS, data, code)
  }

  /**
   * This will handle the Future (asynchronous call) to mry in a failure case
   */
  def handleFailures(request: InMessage): PartialFunction[Throwable, Unit] = {

    case NotFoundException(elem) => respondNotFound(request, elem)
    case ConflictException(value) => respondConflict(request, value.toString)
    case e: Exception => respondError(request, e.toString)
  }
  case class NotFoundException(name: String) extends Throwable
  case class ConflictException(value: MapValue) extends Throwable
}

/**
 * Methods that can extract parameters from an http_api query defined by the user.
 * e.g.: In the spkr query: http://spkServer:9999/messages/23
 *       we could extract the messageId parameter worth 23.
 */
trait ParamExtractor {
  def getParamValues(request: InMessage, name: String): Option[Seq[String]] = {
    val values = request.parameters.getOrElse(name, null)

    values match {
      case null =>
        None

      case MList(strSeq) =>
        Some(strSeq.map(_.asInstanceOf[MString].value).toSeq)

      case MString(str) =>
        Some(Seq(str))

      case _ =>
        throw new RuntimeException("Parameter value has an unsupported type: " + values.getClass)
    }
  }

  def getParamValue(request: InMessage, name: String): Option[String] = {
    getParamValues(request, name) match {
      case Some(values) if values.size > 0 => Some(values(0))
      case _ => None
    }
  }
  def getValidatedKey(request: InMessage, name: String): String = {
    getParamValue(request, name) match {
      case Some(value) => value
      case None => throw new Exception(name + " not found")
    }
  }
}
/**
 * An object containing the http header for every http_api message.
 */
object ResponseHeader {
  val RESPONSE_HEADERS = Map (
    "Content-Type" -> "application/json; charset=UTF-8",
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Methods" -> "GET,POST,DELETE,PUT,OPTIONS",
    "Access-Control-Allow-Headers" -> "Content-Type"
  )
}

/**
 * Contains methods to insert new data in mry using the specified key or an automatically generated key from scn.
 */
trait DatabaseHelper {

    protected def insertWithScnSequence(db:MrySpkrDatabase, scn: ScnClient, token: Long,
                                      sequenceName: String, keyName: String,
                                      newRecord: Map[String, Any], tableAccessor: (OperationApi) => Variable,
                                      keyPrefix: String = ""): Future[Value] = {
    var newObj = newRecord
    val p = Promise[Value]

    // Insert with scn key
    scn.fetchSequenceIds(sequenceName, (sequence: Seq[Long], optException) => {
      optException.headOption match {
        case e: Exception => p.tryFailure(e)
        case _ => {
          val key = keyPrefix + sequence(0)
          newObj += (keyName -> key)
          val insertFuture = insertWithKey(db, key, newObj, tableAccessor)
          insertFuture onFailure { case e: Exception =>  p.tryFailure(e) }
          insertFuture onSuccess { case value => p.trySuccess(value) }
        }
      }
    }, 1, token)
    p future
  }

  protected def insertWithScnSequence(db:MrySpkrDatabase, scn: ScnClient, token: Long,
                                      model: Model,
                                      newRecord: Map[String, Any], keyPrefix: String = "")
                                     (tableAccessor: (OperationApi) => Variable): Future[Value] = {
    insertWithScnSequence(db, scn, token, model.name, model.id, newRecord, tableAccessor, keyPrefix)
  }

  protected def insertWithKey(db:MrySpkrDatabase, key: String, newObj: Map[String, Any],
                              tableAccessor: (OperationApi) => Variable): Future[Value] = {
    val p = Promise[Value]

      db.execute(b => {
        val table = tableAccessor(b)
        table.set(key, newObj)
        b.returns(table.get(key))
      }, (values, optException) => {
        optException.headOption match {
          case e: Exception => p.tryFailure(e)
          case _ => p.trySuccess(values.headOption.getOrElse(""))
        }
      }
    )
    p future
  }
}

/**
 *  Methods used to parse an mry query result to clean JSON usable by the javascript client.
 */
trait JsonHelper {

  protected def getJsonBody(request: InMessage) = {
    request.getData[JObject]
  }
  protected def convertJsonValue(json: JObject, model: Model): Map[String, Any] = {
    json.values collect {
      case (k, v) if model.definition.contains(k) => (k -> convertFieldValue(k, v, model))
    }
  }

  /**
   * Convert field value to usable type
   */
  protected def convertFieldValue(key: String, value: Any, model: Model) = {
    model.definition(key) match {
      case PropertyType.Number => value match {
        case s: String => s.toLong
        case bi: BigInt => bi.longValue()
        case _ => value.toString.toLong
      }
      case PropertyType.Float => value match {
        case s: String => s.toDouble
        case bd: BigDecimal => bd.toDouble
        case d: Double => d
        case _ => value.toString.toDouble
      }
      case PropertyType.Bool => value match {
        case s: String => Seq("true", "1").contains(s)
        case i: Int => (i == 1)
        case b: Boolean => b
        case bi: BigInt => (bi.intValue() == 1)
        case _ => value.toString.toBoolean
      }
      case PropertyType.MapList => value match {
        case seq: Seq[Map[String, String]] => seq
        case map: Map[String, String] => Seq(map)
      }
      case PropertyType.StringList => value match {
        case seq: Seq[String] => seq
        case str: String => str :: Nil
      }
      case _ => value.toString
    }
  }
}



