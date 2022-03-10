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
      needs = List("run_Jmh_current_BenchMark"),
      steps = List(
        WorkflowStep.Use(
          ref = UseRef.Public("actions", "download-artifact", "v3"),
          Map(
            "name" -> "jmh_result"
          )
        ),
        WorkflowStep.Run(
          commands = List("""while IFS= read -r line; do
                            |echo "Text read from file: $line"
                            |IFS=' ' read -ra PARSED_RESULT <<< "$line"
                            |SUBSTR=$(echo ${PARSED_RESULT[1]} | cut -d'.' -f 2)
                            |echo $SUBSTR
                            |B_NAME=$(echo "benchmark_${SUBSTR}")
                            |echo $B_NAME
                            |B_VALUE=$(echo "${PARSED_RESULT[1]}": "${PARSED_RESULT[4]}" ops/sec"")
                            |echo $B_VALUE
                            |echo "::set-output name=$(echo $B_NAME)::$(echo $B_VALUE)"
                            |done < HttpCollectEval.txt""".stripMargin),
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
                 |${{steps.echo_value.outputs.benchmark_benchmarkApp}}
                 |${{steps.echo_value.outputs.benchmark_benchmarkBase}}""".stripMargin,
          ),
        ),
      ),
    ),

  )

  def apply(): Seq[WorkflowJob] = jmhBenchmark()
}
