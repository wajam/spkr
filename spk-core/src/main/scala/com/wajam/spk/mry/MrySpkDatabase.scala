package com.wajam.spk.mry

import com.wajam.mry.ConsistentDatabase
import com.wajam.mry.storage.mysql.{Table, Model, MysqlStorageConfiguration, MysqlStorage}
import com.wajam.spk.config.SpkConfig


/**
 * A custom MySQL data store implementation using the mry framework abstractions. This class adds table
 * definitions and targets the database using the configuration file.
 * When instanciating the database components, if the tables do not exist in the database, the mry framework
 * will create them.
 */
class MrySpkDatabase(config: SpkConfig) extends ConsistentDatabase[MysqlStorage]() {

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
    val STORE_TYPE = "mysql"
    val MEMBER_TABLE = "member"
    val SUBSCRIPTION_TABLE = "subscription"
    val SUBSCRIBER_TABLE = "subscriber"
    val FEEDMESSAGE_TABLE = "feedMessage"
    val POSTMESSAGE_TABLE = "postMessage"
    val NAME_TABLE = "name"

  val tableMember = addTable(new Table(MEMBER_TABLE))
  val tableSubscription = tableMember.addTable(new Table(SUBSCRIPTION_TABLE))
  val tableSubscribers = tableMember.addTable(new Table(SUBSCRIBER_TABLE))
  val tableFeedMessage = tableMember.addTable(new Table(FEEDMESSAGE_TABLE))
  val tablePostMessage = tableMember.addTable(new Table(POSTMESSAGE_TABLE))

  val tableName = addTable(new Table(NAME_TABLE))
}
