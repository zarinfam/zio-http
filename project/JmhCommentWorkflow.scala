import BuildHelper.Scala213
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhCommentWorkflow {
  def jmhBenchmark() = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "comment_jmh_current",
      name = "comment_jmh_current",
      scalas = List(Scala213),
      steps = List(
        WorkflowStep.Use(
          ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
          params = Map(
            "sha"  -> "${{github.sha}}",
            "body" ->
              s"""
                 |**\uD83D\uDE80 Jmh Benchmark:**
                 |
                 |- Current Branch:
                 |""".stripMargin,
          ),
        ),
      ),
    ),

  )

  def apply(): Seq[WorkflowJob] = jmhBenchmark()
}
