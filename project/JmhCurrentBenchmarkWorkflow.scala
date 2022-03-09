import BuildHelper.{JmhVersion, Scala213}
import sbt.{**, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhCurrentBenchmarkWorkflow {

  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""
  val scalaSources: PathFilter = ** / "*.scala"

  val l = List(
    """ sbt -no-colors -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 HttpCollectEval" | tee HttpCollectEval
      |    grep "thrpt" HttpCollectEval | tee test
      |          echo "$test"
      |          while IFS= read -r line; do
      |                echo "Text read from file: $line"
      |                IFS=' ' read -ra PARSED_RESULT <<< "$line"
      |                echo ::set-output name=benchmark_HttpCollectEval_${PARSED_RESULT[1]}::$(echo ${PARSED_RESULT[1]}": "${PARSED_RESULT[4]}) | tee test2
      |                done < test2
      |                 body=$(cat test2)
      |          echo ::set-output name=body::$body"""".stripMargin,
  )

  def jmhBenchmark() = Seq(
    WorkflowJob(
      id = "run_Jmh_current_BenchMark",
      name = "Jmh_Benchmark",
      scalas = List(Scala213),
      steps = List(
        WorkflowStep.Use(
          UseRef.Public("actions", "setup-java", s"v2"),
          Map(
            "distribution" -> "temurin",
            "java-version" -> "8"
          )
        ),
          WorkflowStep.Run(
            env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
            commands = List("cd zio-http", s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt") ++ l,
            id = Some("run_benchmark"),
            name = Some("run_benchmark"),
          )
      )
    )
  )

  def apply(): Seq[WorkflowJob] = jmhBenchmark()

}