# geektime_homework06
## 1. 基本信息

学号: G20210735010190

## 2. 命令及运行截图
### 作业一
#### 步骤
1. 克隆源码
`git clone -b v3.1.2 https://github.com/apache/spark`
2. 使用idea打开
3. 下载antlr4插件，修改SqlBase.g4文件
```
statement
    | SHOW VERSION                     #showVersion

ansiNonReserved
    | VERSION

nonReserved
    | VERSION

VERSION: 'VERSION' | 'VER';
```
4. 使用antlr4工具进行编译

![antlr4](https://user-images.githubusercontent.com/23160530/132961956-a9a5c732-8a16-4d38-a734-f664638fb551.png)

5. 修改SparkSqlParser.scala文件，重写`visitShowVersion`
```
override def visitShowVersion(ctx: ShowVersionContext): LogicalPlan = withOrigin(ctx) {
    ShowVersionCommand()
}
```
6. 实现`ShowVersionCommand`类
```
package org.apache.spark.sql.execution.command

import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.catalyst.expressions.{Attribute, AttributeReference}
import org.apache.spark.sql.types.StringType

case class ShowVersionCommand() extends LeafRunnableCommand {
    override val output: Seq[Attribute] =
        Seq(AttributeReference("version", StringType, true)())

    override def run(sparkSession: SparkSession): Seq[Row] = {
        val spark_version = sparkSession.sparkContext.version
        val java_version = System.getProperty("java.version")
        val spark_version_result = s"spark version: ${spark_version}"
        val java_version_result: String = s"java version: ${java_version}"
        Seq(Row(spark_version_result), Row(java_version_result))
    }
}
```
7. 编译spark源码
```
cd ~/code2021/scala/homework06/
./build/sbt package -DskipTests -Phive -Phive-thriftserver
```
8. 测试
```
bin/spark-sql
spark-sql> show version;
```
#### 运行截图
![spark sql](https://user-images.githubusercontent.com/23160530/132961945-3a235a5c-4188-4e7d-b338-478e9dfbada2.png)

### 作业二
#### 1. apply下面三条优化规则： CombineFilters CollapseProject BooleanSimplification
```
select name from (select name, age, sex from student where age>15 and sex=0) t where t.age>12;
```
##### CombineFilters
采用PushDownPredicates替代了CombineFilters 规则， 源码中调用了CombineFilters
```
=== Applying Rule org.apache.spark.sql.catalyst.optimizer.PushDownPredicates ===
 Project [name#10]                                                                                                                                                Project [name#10]
!+- Filter (age#11 > 12)                                                                                                                                          +- Filter (((age#11 > 15) AND NOT sex#12) AND (age#11 > 12))
!   +- Filter ((age#11 > 15) AND NOT sex#12)                                                                                                                         +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#10, age#11, sex#12], Partition Cols: []]
!      +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#10, age#11, sex#12], Partition Cols: []] 
```
![PushDown](https://user-images.githubusercontent.com/23160530/132961842-f2ea1524-7f61-40d2-9b20-dd8c11ce109d.png)
##### CollapseProject
```
=== Applying Rule org.apache.spark.sql.catalyst.optimizer.CollapseProject ===
 Project [name#15]                                                                                                                                                Project [name#15]
!+- Project [name#15]                                                                                                                                             +- Filter (((age#16 > 15) AND NOT sex#17) AND (age#16 > 12))
!   +- Filter (((age#16 > 15) AND NOT sex#17) AND (age#16 > 12))                                                                                                     +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#15, age#16, sex#17], Partition Cols: []]
!      +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#15, age#16, sex#17], Partition Cols: []] 
```
![collapse](https://user-images.githubusercontent.com/23160530/132961940-f0ba0986-ca74-487b-ae92-7fef98777648.png)

##### BooleanSimplification
```
=== Applying Rule org.apache.spark.sql.catalyst.optimizer.BooleanSimplification ===
 Project [customerId#11]                                                                                                                                                          Project [customerId#11]
!+- Filter ((true AND (amountPaid#13 > 300)) AND (amountPaid#13 < 500))                                                                                                           +- Filter ((amountPaid#13 > 300) AND (amountPaid#13 < 500))
    +- HiveTableRelation [`default`.`sales`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [customerId#11, productName#12, amountPaid#13], Partition Cols: []]      +- HiveTableRelation [`default`.`sales`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [customerId#11, productName#12, amountPaid#13], Partition Cols: []]
```
![boolean](https://user-images.githubusercontent.com/23160530/132961930-2dde3d4e-86e1-4736-9789-6732591962c5.png)

#### 2. apply下面五条优化规则： ConstantFolding PushDownPredicates ReplaceDistinctWithAggregate ReplaceExceptWithAntiJoin FoldablePropagation
```
(
	select distinct name, length('student') as size from
	(select name, age from student where sex=1) a
	where a.age>12
	order by size
)
except
(
	select b.name , length('student')
	from (
		select distinct name, age, sex  
		from student
	) b
	where b.name="wang"
);
```
##### ConstantFolding
```
=== Applying Rule org.apache.spark.sql.catalyst.optimizer.ConstantFolding ===
!Aggregate [name#11, length(student)], [name#11, length(student) AS size#10]                                                                                               Aggregate [name#11, 7], [name#11, 7 AS size#10]
!+- Sort [length(student) ASC NULLS FIRST], true                                                                                                                           +- Sort [7 ASC NULLS FIRST], true
!   +- Join LeftAnti, ((name#11 <=> name#14) AND (length(student) <=> length(student)))                                                                                       +- Join LeftAnti, ((name#11 <=> name#14) AND true)
!      :- Aggregate [name#11, length(student)], [name#11, length(student) AS size#10]                                                                                            :- Aggregate [name#11, 7], [name#11, 7 AS size#10]
!      :  +- Project [name#11, length(student) AS size#10]                                                                                                                       :  +- Project [name#11, 7 AS size#10]
       :     +- Filter (sex#13 AND (age#12 > 12))                                                                                                                                :     +- Filter (sex#13 AND (age#12 > 12))
       :        +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#11, age#12, sex#13], Partition Cols: []]         :        +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#11, age#12, sex#13], Partition Cols: []]
!      +- Aggregate [name#14, age#15, sex#16], [name#14, length(student) AS length(student)#17]                                                                                  +- Aggregate [name#14, age#15, sex#16], [name#14, 7 AS length(student)#17]
          +- Filter (name#14 = wang)                                                                                                                                                +- Filter (name#14 = wang)
             +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#14, age#15, sex#16], Partition Cols: []]                  +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#14, age#15, sex#16], Partition Cols: []]
```
![constantFolding](https://user-images.githubusercontent.com/23160530/132961857-fb485926-d8af-462c-a3c6-defe55b12670.png)
##### PushDownPredicates
```
=== Applying Rule org.apache.spark.sql.catalyst.optimizer.PushDownPredicates ===
 Aggregate [name#11, size#10], [name#11, size#10]                                                                                                                                Aggregate [name#11, size#10], [name#11, size#10]
 +- Join LeftAnti, ((name#11 <=> name#14) AND (size#10 <=> length(student)#17))                                                                                                  +- Join LeftAnti, ((name#11 <=> name#14) AND (size#10 <=> length(student)#17))
    :- Sort [size#10 ASC NULLS FIRST], true                                                                                                                                         :- Sort [size#10 ASC NULLS FIRST], true
    :  +- Aggregate [name#11, size#10], [name#11, size#10]                                                                                                                          :  +- Aggregate [name#11, size#10], [name#11, size#10]
    :     +- Project [name#11, length(student) AS size#10]                                                                                                                          :     +- Project [name#11, length(student) AS size#10]
!   :        +- Filter (age#12 > 12)                                                                                                                                                :        +- Project [name#11, age#12]
!   :           +- Project [name#11, age#12]                                                                                                                                        :           +- Filter (sex#13 AND (age#12 > 12))
!   :              +- Filter sex#13: boolean                                                                                                                                        :              +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#11, age#12, sex#13], Partition Cols: []]
!   :                 +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#11, age#12, sex#13], Partition Cols: []]      +- Project [name#14, length(student) AS length(student)#17]
!   +- Project [name#14, length(student) AS length(student)#17]                                                                                                                        +- Aggregate [name#14, age#15, sex#16], [name#14, age#15, sex#16]
!      +- Filter (name#14 = wang)                                                                                                                                                         +- Filter (name#14 = wang)
!         +- Aggregate [name#14, age#15, sex#16], [name#14, age#15, sex#16]                                                                                                                  +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#14, age#15, sex#16], Partition Cols: []]
!            +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#14, age#15, sex#16], Partition Cols: []] 
```
![PushDown2](https://user-images.githubusercontent.com/23160530/132961870-b0e7f12d-9ffe-4030-82c0-827997fc8cdb.png)
##### ReplaceDistinctWithAggregate
```
=== Applying Rule org.apache.spark.sql.catalyst.optimizer.ReplaceDistinctWithAggregate ===
!Distinct                                                                                                                                                                        Aggregate [name#11, size#10], [name#11, size#10]
 +- Join LeftAnti, ((name#11 <=> name#14) AND (size#10 <=> length(student)#17))                                                                                                  +- Join LeftAnti, ((name#11 <=> name#14) AND (size#10 <=> length(student)#17))
    :- Sort [size#10 ASC NULLS FIRST], true                                                                                                                                         :- Sort [size#10 ASC NULLS FIRST], true
!   :  +- Distinct                                                                                                                                                                  :  +- Aggregate [name#11, size#10], [name#11, size#10]
    :     +- Project [name#11, length(student) AS size#10]                                                                                                                          :     +- Project [name#11, length(student) AS size#10]
    :        +- Filter (age#12 > 12)                                                                                                                                                :        +- Filter (age#12 > 12)
    :           +- Project [name#11, age#12]                                                                                                                                        :           +- Project [name#11, age#12]
    :              +- Filter sex#13: boolean                                                                                                                                        :              +- Filter sex#13: boolean
    :                 +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#11, age#12, sex#13], Partition Cols: []]      :                 +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#11, age#12, sex#13], Partition Cols: []]
    +- Project [name#14, length(student) AS length(student)#17]                                                                                                                     +- Project [name#14, length(student) AS length(student)#17]
       +- Filter (name#14 = wang)                                                                                                                                                      +- Filter (name#14 = wang)
!         +- Distinct                                                                                                                                                                     +- Aggregate [name#14, age#15, sex#16], [name#14, age#15, sex#16]
             +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#14, age#15, sex#16], Partition Cols: []]                        +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#14, age#15, sex#16], Partition Cols: []]
```
![Replace](https://user-images.githubusercontent.com/23160530/132961892-953ccefa-1cef-4c98-95d4-d5e7c687f72b.png)
##### ReplaceExceptWithAntiJoin
```
=== Applying Rule org.apache.spark.sql.catalyst.optimizer.ReplaceExceptWithAntiJoin ===
!Except false                                                                                                                                                                 Distinct
!:- Sort [size#10 ASC NULLS FIRST], true                                                                                                                                      +- Join LeftAnti, ((name#11 <=> name#14) AND (size#10 <=> length(student)#17))
!:  +- Distinct                                                                                                                                                                  :- Sort [size#10 ASC NULLS FIRST], true
!:     +- Project [name#11, length(student) AS size#10]                                                                                                                          :  +- Distinct
!:        +- Filter (age#12 > 12)                                                                                                                                                :     +- Project [name#11, length(student) AS size#10]
!:           +- Project [name#11, age#12]                                                                                                                                        :        +- Filter (age#12 > 12)
!:              +- Filter sex#13: boolean                                                                                                                                        :           +- Project [name#11, age#12]
!:                 +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#11, age#12, sex#13], Partition Cols: []]      :              +- Filter sex#13: boolean
!+- Project [name#14, length(student) AS length(student)#17]                                                                                                                     :                 +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#11, age#12, sex#13], Partition Cols: []]
!   +- Filter (name#14 = wang)                                                                                                                                                   +- Project [name#14, length(student) AS length(student)#17]
!      +- Distinct                                                                                                                                                                  +- Filter (name#14 = wang)
!         +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#14, age#15, sex#16], Partition Cols: []]                     +- Distinct
!                                                                                                                                                                                         +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#14, age#15, sex#16], Partition Cols: []]
```
![ReplaceExceptWithAntiJoin](https://user-images.githubusercontent.com/23160530/132961909-e37112f2-9e11-40b7-8a76-df4aa0d586cf.png)
##### FoldablePropagation
```
=== Applying Rule org.apache.spark.sql.catalyst.optimizer.FoldablePropagation ===
!Aggregate [name#11, size#10], [name#11, size#10]                                                                                                                          Aggregate [name#11, length(student)], [name#11, length(student) AS size#10]
!+- Sort [size#10 ASC NULLS FIRST], true                                                                                                                                   +- Sort [length(student) ASC NULLS FIRST], true
!   +- Join LeftAnti, ((name#11 <=> name#14) AND (size#10 <=> length(student)#17))                                                                                            +- Join LeftAnti, ((name#11 <=> name#14) AND (length(student) <=> length(student)))
!      :- Aggregate [name#11, size#10], [name#11, size#10]                                                                                                                       :- Aggregate [name#11, length(student)], [name#11, length(student) AS size#10]
       :  +- Project [name#11, length(student) AS size#10]                                                                                                                       :  +- Project [name#11, length(student) AS size#10]
       :     +- Filter (sex#13 AND (age#12 > 12))                                                                                                                                :     +- Filter (sex#13 AND (age#12 > 12))
       :        +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#11, age#12, sex#13], Partition Cols: []]         :        +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#11, age#12, sex#13], Partition Cols: []]
       +- Aggregate [name#14, age#15, sex#16], [name#14, length(student) AS length(student)#17]                                                                                  +- Aggregate [name#14, age#15, sex#16], [name#14, length(student) AS length(student)#17]
          +- Filter (name#14 = wang)                                                                                                                                                +- Filter (name#14 = wang)
             +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#14, age#15, sex#16], Partition Cols: []]                  +- HiveTableRelation [`default`.`student`, org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, Data Cols: [name#14, age#15, sex#16], Partition Cols: []]
```
![FoldablePro](https://user-images.githubusercontent.com/23160530/132961923-0f76bc25-8382-4318-a691-f967fad5d202.png)

### 作业三
