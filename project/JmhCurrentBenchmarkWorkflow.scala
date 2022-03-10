import BuildHelper.{JmhVersion, Scala213}
import sbt.{**, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhCurrentBenchmarkWorkflow {

  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""
  val scalaSources: PathFilter = ** / "*.scala"

  val l = List(
    """sbt -no-colors -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 HttpCollectEval" | grep "thrpt" | tee ../HttpCollectEval.txt
      |echo "$(<../HttpCollectEval.txt)"
      |""".stripMargin
  )

  def jmhBenchmark() = Seq(
    WorkflowJob(
      id = "run_Jmh_current_BenchMark",
      name = "Jmh_Benchmark",
      oses = List("centos"),
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
          ),
        WorkflowStep.Use(
         UseRef.Public("actions", "upload-artifact", s"v3"),
          Map(
            "name" -> "jmh_result",
            "path" -> "HttpCollectEval.txt"
          )
        )
      )
    )
  )

  def apply(): Seq[WorkflowJob] = jmhBenchmark()

}