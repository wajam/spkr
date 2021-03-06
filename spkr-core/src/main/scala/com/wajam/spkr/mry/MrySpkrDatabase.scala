package com.wajam.spkr.mry

import com.wajam.mry.{Database, ConsistentDatabase}
import com.wajam.mry.storage.mysql.{Table, Model, MysqlStorageConfiguration, MysqlStorage}
import com.wajam.spkr.config.SpkrConfig
import com.wajam.nrv.service.TokenRange
import com.wajam.nrv.consistency.{MemberConsistencyState, ConsistencyStateTransitionEvent}
import com.wajam.commons.Event


/**
 * A custom MySQL data store implementation using the mry framework abstractions.
 * A Database is a type of distributed Service (nrv), define in mry as a distributed store.
 * This class adds table definitions and creates the database using the configuration file.
 * When instantiating the database components, if the tables do not exist in the database, the mry super
 * class implementation will create them.
 */
class MrySpkrDatabase(name:String, config: SpkrConfig) extends Database(name) {

  val mysqlStorage = new MysqlStorage(
    MysqlStorageConfiguration(
      MrySpkrDatabaseModel.STORE_TYPE,
      config.getMryMysqlServer,
      config.getMryMysqlDb,
      config.getMryMysqlUsername,
      config.getMryMysqlPassword,
      gcTokenStep = 1000000000L  // This will accelerate the garbage collection but does not scale with a lots of data.
    )
  )

  // create and sync model
  val model = MrySpkrDatabaseModel
  mysqlStorage.syncModel(model)

  // register the storage engine
  registerStorage(mysqlStorage)

  // For data consistency reasons, MRY never updates a record. Instead, in inserts a new one with an identical key and
  // token. This creates redundant records that need to be garbage collected.
  mysqlStorage.GarbageCollector.setCollectedRanges(List(TokenRange(0,TokenRange.MaxToken))) // If the db contained other shards, the proper range should be set here.
  mysqlStorage.GarbageCollector.start()
  // TODO: with a dynamic cluster using replication, the shard ranges may change at runtime and should be updated through an observer.

  def stopStorage() {
    mysqlStorage.stop()
  }
}

object MrySpkrDatabaseModel extends Model {
    // The store type is a name necessary to target the right storage, but multiple storage when using
    // consistencyMasterSlave for replication support is not yet implemented .
    val STORE_TYPE = "mysql"

    val MEMBER_TABLE = "members"
    val SUBSCRIPTION_TABLE = "subscriptions"
    val SUBSCRIBER_TABLE = "subscribers"
    val FEED_MESSAGE_TABLE = "feedMessages"
    val POST_MESSAGE_TABLE = "postMessages"
    val NAME_TABLE = "names"

  val tableMember = addTable(new Table(MEMBER_TABLE))
  val tableSubscription = tableMember.addTable(new Table(SUBSCRIPTION_TABLE))
  val tableSubscribers = tableMember.addTable(new Table(SUBSCRIBER_TABLE))
  val tableFeedMessage = tableMember.addTable(new Table(FEED_MESSAGE_TABLE))
  val tablePostMessage = tableMember.addTable(new Table(POST_MESSAGE_TABLE))

  val tableName = addTable(new Table(NAME_TABLE))
}
