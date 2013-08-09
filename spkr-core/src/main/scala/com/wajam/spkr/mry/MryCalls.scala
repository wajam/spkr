package com.wajam.spkr.mry

import com.wajam.scn.client.ScnClient
import com.wajam.nrv.utils.{Promise, Future}
import com.wajam.mry.execution.{Variable, OperationApi, Value}
import com.wajam.mry.execution.Implicits._
import com.wajam.spkr.mry.model.Model

/**
 * This class centralize all calls to the mry data store used by MryResources and PercolationResources.
 * It increases reusability.
 * TODO: refactor to centralize a token/model/map into a single class "entity"
 */
class MryCalls(val db: MrySpkrDatabase, val scn: ScnClient) extends InsertHelper {

  def getFeedFromUsername(username: String): Future[scala.Seq[Value]] = {
    db.execute(b => {
      b.returns(b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(username)
        .from(MrySpkrDatabaseModel.FEED_MESSAGE_TABLE).get())
    })
  }

  def getSubscribersFromUsername(username: String): Future[scala.Seq[Value]] = {
    db.execute(b => {
      b.returns(b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(username)
        .from(MrySpkrDatabaseModel.SUBSCRIBER_TABLE).get())
    })
  }

  def getMemberFromUsername(username: String): Future[scala.Seq[Value]] = {
    db.execute(b => {
      b.returns(b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(username))
    })
  }

  def getSubscriptionsFromUserName(username: String): Future[scala.Seq[Value]] = {
    db.execute(b => {
      b.returns(b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(username)
        .from(MrySpkrDatabaseModel.SUBSCRIPTION_TABLE).get())
    })
  }

  def insertMessage(username: String, token: Long, model: Model, message: Map[String, Any]): Future[Value] = {
    insertWithScnSequence(token, model, message) {
      _.from(MrySpkrDatabaseModel.STORE_TYPE).
        from(MrySpkrDatabaseModel.MEMBER_TABLE).
        get(username).
        from(MrySpkrDatabaseModel.POST_MESSAGE_TABLE)
    }
  }

  def insertMember(username: String, member: Map[String, Any]): Future[Value] = {
    insertWithKey(username, member) {
      _.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE)
    }
  }

  def insertSubscription(subscriberUsername: String, targetUsername: String, subscription: Map[String, Any]): Future[Value] = {
    insertWithKey(targetUsername, subscription) {
      _.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(subscriberUsername).from(MrySpkrDatabaseModel.SUBSCRIPTION_TABLE)
    }
  }
  def insertSubscriber(targetUsername: String, subscriberUsername: String, subscriber: Map[String, Any]): Future[Value] = {
    insertWithKey(subscriberUsername, subscriber){
      _.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(targetUsername).from(MrySpkrDatabaseModel.SUBSCRIBER_TABLE)
    }
  }

  def insertFeedEntry(targetFeedUsername: String, token: Long, model: Model, feedMessage: Map[String, Any]): Future[Value] = {
    insertWithScnSequence(token,model,feedMessage) {
      _.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(targetFeedUsername).from(MrySpkrDatabaseModel.FEED_MESSAGE_TABLE)
    }
  }

  def deleteSubscriber(subscriberUsername: String, targetUsername: String): Future[scala.Seq[Value]] = {
    db.execute(b => {
      b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(targetUsername).
        from(MrySpkrDatabaseModel.SUBSCRIBER_TABLE).delete(subscriberUsername)
    })
  }

  def deleteSubscription(subscriberUsername: String, targetUsername: String): Future[scala.Seq[Value]] = {
    db.execute(b => {
      b.from(MrySpkrDatabaseModel.STORE_TYPE).from(MrySpkrDatabaseModel.MEMBER_TABLE).get(subscriberUsername).
        from(MrySpkrDatabaseModel.SUBSCRIPTION_TABLE).delete(targetUsername)
    })
  }

}

/**
 * Contains methods to insert new data in mry using the specified key or an automatically generated key from scn.
 */
trait InsertHelper {

  def db: MrySpkrDatabase

  def scn: ScnClient

  protected def insertWithScnSequence(token: Long, model: Model, newRecord: Map[String, Any])(tableAccessor: (OperationApi) => Variable): Future[Value] = {
    var newObj = newRecord
    val p = Promise[Value]

    // Insert with scn key
    scn.fetchSequenceIds(model.name, (sequence: Seq[Long], optException) => {
      optException.headOption match {
        case Some(e: Exception) => p.tryFailure(e)
        case _ => {
          val key = sequence(0).toString
          newObj += (model.id -> key)
          val insertFuture = insertWithKey(key, newObj) {
            tableAccessor(_)
          }
          insertFuture onFailure {
            case e: Exception => p.tryFailure(e)
          }
          insertFuture onSuccess {
            case value => p.trySuccess(value)
          }
        }
      }
    }, 1, token)
    p future
  }

  protected def insertWithKey(key: String, newRecord: Map[String, Any])
                             (tableAccessor: (OperationApi) => Variable): Future[Value] = {
    val p = Promise[Value]

    db.execute(b => {
      val table = tableAccessor(b)
      table.set(key, newRecord)
      b.returns(table.get(key))
    }, (values, optException) => {
      optException.headOption match {
        case Some(e: Exception) => p.tryFailure(e)
        case _ => p.trySuccess(values.headOption.getOrElse(""))
      }
    })
    p future
  }
}