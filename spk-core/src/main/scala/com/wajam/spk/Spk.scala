package com.wajam.spk

import com.wajam.spk.config.SpkConfig
import com.wajam.spk.mry.MrySpkDatabase

/**
 *
 */

object Spk extends App {

  val config = SpkConfig.fromDefaultConfigurationPath

  // MRY database & binding
  val mryDb = new MrySpkDatabase(config)

  System.out.println("now speaking.")

}
