package com.wajam.spk.mry

import com.wajam.mry.ConsistentDatabase
import com.wajam.mry.storage.mysql.{Table, Model, MysqlStorageConfiguration, MysqlStorage}
import com.wajam.spk.config.SpkConfig


/**
 * A custom MySQL data store implementation using the mry framework abstractions.
 * A ConsistentDatabase is a type of distributed Service (nrv), define in mry as a distributed store.
 * This class adds table definitions and creates the database using the configuration file.
 * When instanciating the database components, if the tables do not exist in the database, the mry super
 * class implementation will create them.
 */
class MrySpkDatabase(name:String, config: SpkConfig) extends ConsistentDatabase[MysqlStorage](name) {

  val mysqlStorage = new MysqlStorage(MysqlStorageConfiguration(MrySpkDatabaseModel.STORE_TYPE,
    config.getMryMysqlServer,
    config.getMryMysqlDb,
    config.getMryMysqlUsername,
    config.getMryMysqlPassword))

  // create and sync model
  val model = MrySpkDatabaseModel
  mysqlStorage.syncModel(model)

  // register the storage engine
  registerStorage(mysqlStorage)

  //TODO: update garbage collection range onRangeConsistencyAvailabilityChange
  //addObserver(GCUpdater)

  def stopStorage() {
    mysqlStorage.stop()
  }
}

object MrySpkDatabaseModel extends Model {
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
