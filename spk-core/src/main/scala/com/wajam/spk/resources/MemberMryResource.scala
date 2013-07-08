package com.wajam.spk.resources

import com.wajam.nrv.data.InMessage
import com.wajam.spk.mry.{MrySpkDatabaseModel, MrySpkDatabase}
import com.wajam.mry.execution.{MapValue, Value, OperationApi}
import com.wajam.mry.execution.Implicits._
import com.wajam.scn.client.ScnClient

/**
 *
 */
class MemberMryResource(db: MrySpkDatabase, scn: ScnClient) extends MryResource(db,scn) {

  // All the fields associated with a member
  val id = "member_id"
  val displayName = "display_name"

  /**
   *
   */
  override def get(request: InMessage) {
    println("Received GET request on member resource... " + request)
    db.execute(b => {
      b.returns(b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE).get(getValidatedNumericKey(request, id)))
    }).
      onFailure (handleFailures(request)).
      onSuccess { case Seq(MapValue(member), _*) => {
      //TODO: somehow parse the mry structure into a Json Map before it is sent.
      //member.map((s,v) => (s.toString -> v.toString))
      //case user: MapValue => request.respond(JsonConverter.toJsonObject(user, model, request.getProjection))
        request.reply(Map(), RESPONSE_HEADERS, Map("member_keys_will_be_here" -> "and_other_fields_here"))
      }
    }
  }

  /**
   *
   */
  override def create(request: InMessage) {
    println("Received CREATE request on member resource..." + request.toString)

    /**
     * TODO: convert body from JSon to something usable.
     */
    val member = Map[String,Any](displayName -> "Gustave")

    insertWithScnSequence(db, scn, request, id, id, member,
      keyPrefix = "",
      tableAccessor = (b: OperationApi) => {
        b.from(MrySpkDatabaseModel.STORE_TYPE).from(MrySpkDatabaseModel.MEMBER_TABLE)
      }, callback = (value) => {
        //println("call back called!!! got this data: " + value)
        //TODO: percolate here (timeline)
      }
    )
  }
}
