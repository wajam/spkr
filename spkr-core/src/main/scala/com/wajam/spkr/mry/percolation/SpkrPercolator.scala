package com.wajam.spkr.mry.percolation

import com.wajam.spnl._
import com.wajam.mry.storage.mysql.{TableContinuousFeeder, TableAllLatestFeeder, TableTimelineFeeder, Table}
import com.wajam.nrv.protocol.codec.GenericJavaSerializeCodec
import com.wajam.spkr.mry.{MryCalls, MrySpkrDatabase}
import com.wajam.spnl.feeder.Feeder._
import com.wajam.nrv.service.{MemberStatus, StatusTransitionEvent, ServiceMember}
import com.wajam.mry.execution.MapValue
import scala.collection.mutable
import com.wajam.scn.client.ScnClient
import com.wajam.spnl.feeder.Feeder
import com.wajam.commons.Logging
import com.wajam.spkr.mry.MryExecutionContext

/**
 * This class uses spnl to schedule tasks that will manipulate data in the database based on events, using the logic
 * defined here. This data manipulation is referred to as "percolation". There are 2 types of percolation tasks :
 *  - Continuous tasks, which constantly iterated through the entire local store in a loop and execute their logic on
 *    all data.
 *  - Timeline tasks, which get triggered every time new data is inserted (similar to a database trigger)
 * Those tasks are used to achieve things like:
 *  - Update or delete data using custom logic.
 *  - Insert data that already exists, but has other dependencies that need to be sharded elsewhere
 *    (like a reverse lookup table, since we can usually only get data using a single ID).
 */
class SpkrPercolator(db: MrySpkrDatabase, scn: ScnClient, spnlPersistence: TaskPersistenceFactory) extends Logging {
  val mryCalls = new MryCalls(db,scn)
  // The percolation logic is defined in each one of those classes.
  val subscriberPercolation = new SubscriberPercolationResource(mryCalls) // Builds the list of subscribers for each member as subscriptions are made
  val feedPercolation = new FeedPercolationResource(mryCalls) // Builds the feed for each member based on posted messages and subscribed members

  private val spnl = new Spnl

  // Observe MemberStatus change on the store service, so we can adjust percolation accordingly (cannot percolate on member when status = Down)
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
      new TableAllLatestFeeder(action.name, db.mysqlStorage, table, db.getMemberTokenRanges(member))
        with TableContinuousFeeder
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

  // Used to store all tasks that are currently running in spnl. This will be used to prevent registering the same task twice.
  private val tasks: mutable.Map[ServiceMember, Seq[Task]] = mutable.Map()

  def AcceptAll: Feeder.FeederPredicate = (_: FeederData) => true

  private def initializeMemberTasks(member: ServiceMember) {
    tasks.synchronized {
      if (!tasks.contains(member)) {
        val tokenRanges = db.getMemberTokenRanges(member)
        info("Creating percolation tasks for member %s (%s).".format(member, tokenRanges))
        val memberTasks = Seq(
          createTask(db.model.tableSubscription, registerPercolation("subscriber aggregation", subscriberPercolation), AcceptAll, member, continuous = false),
          createTask(db.model.tablePostMessage, registerPercolation("feed aggregation", feedPercolation),AcceptAll, member, continuous = false)
        )
        tasks += (member -> memberTasks)

        info("Starting percolation tasks for member %s.".format(member))
        memberTasks.foreach(spnl.run(_))
      }
    }
  }

  private def removeMemberTasks(member: ServiceMember) {
    tasks.synchronized {
      if (tasks.contains(member)) {
        info("Stopping percolation tasks for member %s.".format(member))
        val memberTasks = tasks(member)
        memberTasks.foreach(spnl.stop(_))
        tasks -= member
      }
    }
  }

  // Creates and registers a TaskAction using the specified logic
  // TODO: this could be refactored to clearly separate continuous, delete, insert and update percolations
  private def registerPercolation(name: String, percolationResource: PercolationResource): TaskAction = {
    val action = new TaskAction(name, (request: SpnlRequest) => {
      info("Percolating on %s with this message: %s".format(name,request.message))

      // Extracts the data (keys, values, token, timestamp)
      val data = request.message.getData[TaskData]

      // Extract the keys
      val keys: Seq[String] = data.values(TableAllLatestFeeder.Keys) match {
        case k: Seq[_] => k.asInstanceOf[Seq[String]]
        case _ =>  throw new IllegalArgumentException()
      }

      // Extract the record (values)
      // In the case of mutation percolation, the data is a map containing two records: "old_data" and "new_data".
      // Since we only aggregate on insert, we'll assume "old_data" is always worth None to simplify the percolation.
      // see "mutations map" in mry.TableTimelineFeeder for more info on the structure.
      val newRecord = (data.values("new_value"), data.values("old_value")) match {
        case (Some(m: MapValue), None) => Some(m) // case: mutation percolation (INSERT), we only want the new record for percolation
        case (Some(m: MapValue), Some(_)) => Some(m) // case: mutation percolation (UPDATE)
        case (None, Some(m: MapValue)) =>  None // case: mutation percolation (DELETE), don't do anything
        case _ => Some(data.values(TableAllLatestFeeder.Value)) // case: continuous percolation, extract the the record
      }

      //Extract the token
      val token: Long = data.token

      //apply the specified Percolation Resource Task logic to the selected data
      try {
        (keys, newRecord, token) match {
          case (keySeq: Seq[String], Some(valueMap: MapValue), t: Long) => percolationResource.PercolateTaskLogic(keySeq, valueMap, t)
          case _ => // Either the format is invalid, or it's a DELETE and we don't want to percolate
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
