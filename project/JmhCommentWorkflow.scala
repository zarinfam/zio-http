import BuildHelper.Scala213
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhCommentWorkflow {
  def jmhBenchmark() = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "comment_jmh_current",
      name = "comment_jmh_current",
      oses = List("centos"),
      scalas = List(Scala213),
      needs = List("Jmh_Benchmark"),
      steps = List(
        WorkflowStep.Use(
          ref = UseRef.Public("dawidd6", "action-download-artifact", "v2"),
          Map(
            "github_token" -> "${{secrets.ACTIONS_PAT}}",
            "workflow" -> "Jmh_Benchmark"
          )
        ),
        WorkflowStep.Run(
          commands = List("""bash <(value=`cat HttpCollectEval.txt`
                          |echo ::set-output name=result::$value)""".stripMargin),
          id = Some("echo_value"),
          name = Some("echo_value")
        ),
        WorkflowStep.Use(
          ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
          params = Map(
            "sha"  -> "${{github.sha}}",
            "body" ->
              """
                 |**\uD83D\uDE80 Jmh Benchmark:**
                 |
                 |- Current Branch:
                 |${{steps.echo_value.outputs.result}}""".stripMargin,
          ),
        ),
      ),
    ),

  )

  def apply(): Seq[WorkflowJob] = jmhBenchmark()
}
