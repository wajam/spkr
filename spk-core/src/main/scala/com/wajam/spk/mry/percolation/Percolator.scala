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
 * This classes uses spnl to schedule tasks that will manipulate data in the database based on the logic defined here.
 * This is refered to as "percolation". There are 2 types of percoaltion tasks :
 *  - Continuous tasks which constantly iterated through the entire local store in a loop and execute their logic every time.
 *  - Timeline tasks which get triggered every time new data is inserted (similar to a database trigger)
 * Those tasks are used to achive things like:
 *  - Update data based on a set of criterias.
 *  - Insert new data that is already partly inserted, but has other dependecies that need to be sharded elsewhere (like a reverse lookup table).
 *  - Delete records using some particular logic.
 */
class Percolator(db: MrySpkDatabase, scn: ScnClient, spnlPersistence: TaskPersistenceFactory) {
  // The percolation logic is denfined here.
  val subscriberPercolation = new SubscriberPercolationResource(db,scn)
  val feedPercolation = new FeedPercolationResource(db,scn)


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

    new Task(
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
          createTask(db.model.tableSubscription, registerPercolation("subscriber aggregation",subscriberPercolation),
            AcceptAll, member, continuous = false)
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

      //extracts the data
      val data = request.message.getData[Map[String, Any]]
      val keys: Seq[String] = data.get(TableContinuousFeeder.Keys).get match {
        case k: Seq[String] => k
      }
      val values = request.message.getData[Map[String, Any]] // continuous version = data.get(TableContinuousFeeder.Value).get

      //apply the specified Percolation Resource Task logic to the selected data
      try {
        (keys, values) match {
          case (keySeq: Seq[String], valueMap: Map[String, Any]) => percolationResource.PercolateTaskLogic(keySeq, valueMap)
          case _ => println("Invalid percolation values format.")
        }
        request.ok()
      } catch {
        case e: Exception => request.ignore(e)
      }
    },responseTimeout = 1000)
    action.action.applySupport(nrvCodec = Some(new GenericJavaSerializeCodec))
    db.registerAction(action.action)
    action
  }
}
