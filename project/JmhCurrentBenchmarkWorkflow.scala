import BuildHelper.{JmhVersion, Scala213}
import sbt.nio.file.FileTreeView
import sbt.{**, Glob, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhCurrentBenchmarkWorkflow {

  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""
  val scalaSources: PathFilter = ** / "*.scala"
  val files = FileTreeView.default.list(Glob("./zio-http-benchmarks/src/main/scala/zhttp.benchmarks/**"), scalaSources)

  def getFilenames = files.map(file => {
    val path = file._1.toString
    path.replaceAll("^.*[\\/\\\\]", "").replaceAll(".scala", "")
  }).sorted

  def sbtCommand(list: Seq[String], branch: String = "Current") = list.map(str =>
    s"""sbt -no-colors -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" | grep "thrpt" >> ../${branch}_${list.head}.txt""".stripMargin)

  def groupedBenchmarks(batchSize: Int) = getFilenames.grouped(batchSize).toList
  def dependencies(batchSize: Int) = groupedBenchmarks(batchSize).flatMap((l: Seq[String]) => List(s"run_jmh_benchmark_Current_${l.head}",s"run_jmh_benchmark_Main_${l.head}"))

  def downloadArtifact(branch: String, batchSize: Int) = groupedBenchmarks(batchSize).flatMap(l => {
    Seq(
      WorkflowStep.Use(
        ref = UseRef.Public("actions", "download-artifact", "v3"),
        Map(
          "name" -> s"jmh_result_${branch}_${l.head}"
        )
      ),
      WorkflowStep.Run(
        commands = List(
          s"""while IFS= read -r line; do
             |   IFS=' ' read -ra PARSED_RESULT <<< "$$line"
             |   B_VALUE=$$(echo "$${PARSED_RESULT[1]}": "$${PARSED_RESULT[4]}" ops/sec"")
             |   echo $$B_VALUE >> ${branch}.txt
             | done < ${branch}_${l.head}.txt""".stripMargin),
        id = Some(s"result_${branch}_${l.head}"),
        name = Some(s"Result ${branch} ${l.head}")
      )
    )
  })
  def setOutput(branch: String) =     WorkflowStep.Run(
    commands = List(
      s"""body=$$(cat $branch.txt)
         | body="$${body//'%'/'%25'}"
         | body="$${body//$$'\\n'/'%0A'}"
         | body="$${body//$$'\\r'/'%0D'}"
         | echo "$$body"
         | echo "::set-output name=body::$$(echo "$$body")"
         | """.stripMargin
    ),
    id = Some(s"set_output_$branch"),
    name = Some(s"Set Output_$branch")
  )
  def jmhPublish(batchSize: Int) = Seq(WorkflowJob(
  id = "jmh_publish",
  name = "Jmh Publish",
  scalas = List(Scala213),
  needs =  dependencies(batchSize),
  steps = downloadArtifact("Current", batchSize) ++ downloadArtifact("Main", batchSize) ++
    Seq(
      setOutput("Current"),
      setOutput("Main"),
    WorkflowStep.Use(
      ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
      params = Map(
        "sha" -> "${{github.sha}}",
        "body" ->
          """
            |**\uD83D\uDE80 Jmh Benchmark:**
            |
            |- **Current Branch**:
            | ${{steps.set_output_Current.outputs.body}}
            |
            |- **Main Branch**:
            | ${{steps.set_output_Main.outputs.body}}
            | """.stripMargin
      )
    )
  )
)
  )

  def jmhRun(batchSize: Int, branch: String = "Current") = groupedBenchmarks(batchSize).map(l => {
    val checkout = if(branch == "Current") "" else "main"
    WorkflowJob(
      id = s"run_jmh_benchmark_${branch}_${l.head}",
      name = s"Jmh Benchmark ${branch} ${l.head}",
      scalas = List(Scala213),
      steps = List(
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", "v2"),
          Map(
            "path" -> "zio-http",
            "ref" -> checkout
          )
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "setup-java", "v2"),
          Map(
            "distribution" -> "temurin",
            "java-version" -> "8"
          )
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          commands = List("cd zio-http", s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt", s"cat > ${branch}_${l.head}.txt") ++ sbtCommand(l, branch),
          id = Some("run_benchmark"),
          name = Some("Run Benchmark")
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "upload-artifact", "v3"),
          Map(
            "name" -> s"jmh_result_${branch}_${l.head}",
            "path" -> s"${branch}_${l.head}.txt"
          )
        )
      )
    )
  }
  )

  def apply(batchSize: Int): Seq[WorkflowJob] = jmhRun(batchSize) ++ jmhRun(batchSize, "Main") ++ jmhPublish(batchSize)

}