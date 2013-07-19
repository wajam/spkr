package com.wajam.spk.mry.percolation

import com.wajam.spk.resources.{MemberFeedMryResource, MemberMessageMryResource, MemberSubscriptionMryResource, MemberMryResource}
import com.wajam.spnl._
import com.wajam.mry.storage.mysql.{TableTimelineFeeder, Table, TableContinuousFeeder}
import com.wajam.nrv.protocol.codec.GenericJavaSerializeCodec
import com.wajam.spk.mry.MrySpkDatabase
import com.wajam.spnl.feeder.Feeder._
import com.wajam.nrv.service.{MemberStatus, StatusTransitionEvent, ServiceMember}
import com.wajam.mry.execution.MapValue

import scala.collection.mutable
import com.wajam.scn.client.ScnClient
import com.wajam.spnl.feeder.Feeder

/**
 * This class uses spnl to schedule tasks that will manipulate data in the database based events, using the logic defined here.
 * This is refered to as "percolation". There are 2 types of percoaltion tasks :
 *  - Continuous tasks which constantly iterated through the entire local store in a loop and execute their logic on all data.
 *  - Timeline tasks which get triggered every time new data is inserted (similar to a database trigger)
 * Those tasks are used to achive things like:
 *  - Update data based on a set of criterias.
 *  - Insert new data that is already partly inserted, but has other dependecies that need to be sharded elsewhere (like a reverse lookup table).
 *  - Delete records using some particular logic.
 */
class Percolator(db: MrySpkDatabase, scn: ScnClient, spnlPersistence: TaskPersistenceFactory) {
  // The percolation logic is denfined in each one of those classes.
  val subscriberPercolation = new SubscriberPercolationResource(db,scn) // Builds the list of subscribers for each member as subscriptions are made
  val feedPercolation = new FeedPercolationResource(db,scn) // Builds the feed for each memeber based on posted messages and subscribed members

  private val spnl = new Spnl

  // Observe MemberStatus change on the store service, so we can adjuste percolation accordingly (cannot percolate on member when status = Down)
  db.addObserver(event => {
    event match { case event: StatusTransitionEvent =>
        event.to match {
          // Initialize percolation tasks for local service member going up
          case MemberStatus.Up if db.cluster.isLocalNode(event.member.node) => initializeMemberTasks(event.member)
          // Remove percolation tasks for all other cases
          case _ => removeMemberTasks(event.member)
        }
      case _ =>
    }
  })

  // This method will create the spnl Task object from the specified parameters using a default context.
  def createTask(table: Table, action: TaskAction, filter: FeederPredicate, member: ServiceMember, continuous: Boolean) = {
    val feeder = if (continuous) {
      new TableContinuousFeeder(action.name, db.mysqlStorage, table, db.getMemberTokenRanges(member)).withFilter(filter)
    } else {
      new TableTimelineFeeder(action.name, db.mysqlStorage, table, db.getMemberTokenRanges(member)).withFilter(filter)
    }

    new Task (
      feeder = feeder,
      action = action,
      persistence = spnlPersistence.createServiceMemberPersistence(db, member),
      context = new TaskContext(normalRate = 10, throttleRate = 1, maxConcurrent = 5)
    )
  }

  // Used to store all tasks that are currentely running in spnl. This will be used to prevent registering the same task twice.
  private val tasks: mutable.Map[ServiceMember, Seq[Task]] = mutable.Map()

  def AcceptAll: Feeder.FeederPredicate = (data: Map[String,Any]) => true

  private def initializeMemberTasks(member: ServiceMember) {
    tasks.synchronized {
      if (!tasks.contains(member)) {
        val tokenRanges = db.getMemberTokenRanges(member)
        println("Creating percolation tasks for member %s (%s).".format(member, tokenRanges))
        val memberTasks = Seq(
          createTask(db.model.tableSubscription, registerPercolation("subscriber aggregation",subscriberPercolation), AcceptAll, member, continuous = false),
          createTask(db.model.tablePostMessage, registerPercolation("feed aggregation",feedPercolation),AcceptAll, member, continuous = false)
        )
        tasks += (member -> memberTasks)

        println("Starting percolation tasks for member %s.".format(member))
        memberTasks.foreach(spnl.run(_))
      }
    }
  }

  private def removeMemberTasks(member: ServiceMember) {
    tasks.synchronized {
      if (tasks.contains(member)) {
        println("Stopping percolation tasks for member %s.".format(member))
        val memberTasks = tasks(member)
        memberTasks.foreach(spnl.stop(_))
        tasks -= member
      }
    }
  }

  //Creates and registers a TaskAction using the specified logic
  private def registerPercolation(name: String, percolationResource: PercolationResource): TaskAction = {
    val action = new TaskAction(name, (request: SpnlRequest) => {
      println("Percolating on %s with this message: %s".format(name,request.message))

      // Extracts the data (keys, values, token, timestamp)
      val data = request.message.getData[Map[String, Any]]

      // Extract the keys
      val keys: Seq[String] = data.get(TableContinuousFeeder.Keys).get match {
        case k: Seq[String] => k
      }

      // Extract the record (values)
      // In the case of mutation percolation, the data is a map containing two records: "old_data" and "new_data".
      // Since we only aggregate on insert, we'll assume "old_data" is always worth None to simplify the percolation.
      // see "mutations map" in mry.TableTimelineFeeder for more info on the structure.
      val newRecord = data.get("new_value").get match {
        case Some(m: MapValue) => m // case: mutation percolation, only the new record should be kept for percolation
        case _ => data.get(TableContinuousFeeder.Value).get //case: continuous percolation, extract the the record
      }

      //Extract the token
      val token: Long = data.get(TableContinuousFeeder.Token).get match {
        case t: String => t.toLong
      }

      //apply the specified Percolation Resource Task logic to the selected data
      try {
        (keys, newRecord, token) match {
          case (keySeq: Seq[String], valueMap: MapValue, t: Long) => percolationResource.PercolateTaskLogic(keySeq, valueMap, t)
          case _ => println("Invalid percolation values format: keys=%s (expected %s), values=%s (expected %s)".
            format(keys + "(class=%s)".format(keys.getClass), "Seq[String]",newRecord + "(class=%s)".format(newRecord.getClass), "Map[String, Any]" ))
        }
        request.ok()
      } catch {
        case e: Exception => request.ignore(e) ; e.printStackTrace()
      }
    },responseTimeout = 1000)
    action.action.applySupport(nrvCodec = Some(new GenericJavaSerializeCodec))
    db.registerAction(action.action)
    action
  }
}
