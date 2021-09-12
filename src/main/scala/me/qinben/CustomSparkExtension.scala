package me.qinben

import org.apache.spark.internal.Logging
import org.apache.spark.sql.SparkSessionExtensions

class CustomSparkExtension extends (SparkSessionExtensions => Unit) with Logging {
    override def apply(v1: SparkSessionExtensions): Unit = {
        logInfo("Loading MyExtension extension")
        v1.injectOptimizerRule(session =>
            CustomRule(session)
        )
    }
}
