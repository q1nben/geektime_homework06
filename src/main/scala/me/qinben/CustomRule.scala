package me.qinben

import org.apache.spark.internal.Logging
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.expressions.{Literal, Log, Multiply}
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.types.{Decimal, IntegerType}

case class CustomRule(spark: SparkSession) extends Rule[LogicalPlan] with Logging {
    def apply(plan: LogicalPlan): LogicalPlan = plan transformExpressions  {
        case Multiply(left,right,error) if right.isInstanceOf[Literal] &&
                right.asInstanceOf[Literal].value.asInstanceOf[Decimal].toDouble == 1.0 =>
            println("=== Apply CustomOptimizerRule Success ===")
            left
    }
}