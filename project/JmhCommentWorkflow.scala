import BuildHelper.Scala213
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhCommentWorkflow {

  def jmhBenchmark() = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "jmh_publish",
      name = "Jmh Publish",
      oses = List("centos"),
      scalas = List(Scala213),
      needs = List("run_jmh_benchmark"),
      steps = List(
        WorkflowStep.Use(
          ref = UseRef.Public("actions", "download-artifact", "v3"),
          Map(
            "name" -> "jmh_result"
          )
        ),
        WorkflowStep.Run(
          commands = List(""" cat > body.txt
                            | while IFS= read -r line; do
                            |   IFS=' ' read -ra PARSED_RESULT <<< "$line"
                            |   B_VALUE=$(echo "${PARSED_RESULT[1]}": "${PARSED_RESULT[4]}" ops/sec"")
                            |   echo $B_VALUE >> body.txt
                            | done < HttpCollectEval.txt
                            | body=$(cat body.txt)
                            | body="${body//'%'/'%25'}"
                            | body="${body//$'\n'/'%0A'}"
                            | body="${body//$'\r'/'%0D'}"
                            | echo "$body"
                            | echo "::set-output name=body::$(echo "$body")"""".stripMargin),
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
                 | ${{steps.echo_value.outputs.body}}""".stripMargin,
          ),
        ),
      ),
    ),

  )

  def apply(): Seq[WorkflowJob] = jmhBenchmark()
}
