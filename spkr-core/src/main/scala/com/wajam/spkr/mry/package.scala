package com.wajam.spkr

import scala.concurrent.ExecutionContext
import com.wajam.commons.Logging

package object mry {

  implicit object MryExecutionContext extends ExecutionContext with Logging {
    def execute(runnable: Runnable) {
      // Execute in the same thread used by MRY Callback
      runnable.run()
    }

    def reportFailure(t: Throwable) {
      error("Error occured in Future execution", t)
    }

  }
}
