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

  def sbtCommand(list: Seq[String]) = list.map(str =>
    s"""sbt -no-colors -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" | grep "thrpt" >> ../${list.head}.txt""".stripMargin)

  def groupedBenchmarks(batchSize: Int) = getFilenames.grouped(batchSize).toList
  def dependencies(batchSize: Int) = groupedBenchmarks(batchSize).flatMap((l: Seq[String]) => List(s"run_jmh_benchmark_${l.head}"))

  def jmhPublish(batchSize: Int) = Seq(WorkflowJob(
  id = "jmh_publish",
  name = "Jmh Publish",
  scalas = List(Scala213),
  needs =  dependencies(batchSize),
  steps = groupedBenchmarks(batchSize).map(l => {
    WorkflowStep.Use(
      ref = UseRef.Public("actions", "download-artifact", "v3"),
      Map(
        "name" -> s"jmh_result_${l.head}"
      )
    )
  }) ++ Seq(
    WorkflowStep.Run(
      commands = List(
        """rm -f body.txt
        |cat > body.txt
        |echo "::set-output name=res::$(echo "$(<body.txt)")"
        |""".stripMargin),
      id = Some("create_comment"),
      name = Some("Create Comment")
    )
  ) ++ groupedBenchmarks(batchSize).map(l => {
    WorkflowStep.Run(
      commands = List(
        s"""while IFS= read -r line; do
           |   IFS=' ' read -ra PARSED_RESULT <<< "$$line"
           |   B_VALUE=$$(echo "$${PARSED_RESULT[1]}": "$${PARSED_RESULT[4]}" ops/sec"")
           |   echo $$B_VALUE >> body.txt
           | done < ${l.head}.txt""".stripMargin),
      id = Some(s"result_${l.head}"),
      name = Some(s"Result ${l.head}")
    )
  }) ++ Seq(
    WorkflowStep.Run(
      commands = List(
      """body=$(cat body.txt)
        | body="${body//'%'/'%25'}"
        | body="${body//$'\n'/'%0A'}"
        | body="${body//$'\r'/'%0D'}"
        | echo "$body"
        | echo "::set-output name=body::$(echo "$body")"""".stripMargin
      ),
      id = Some("set_output"),
      name = Some("Set Output")
    ),
    WorkflowStep.Use(
      ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
      params = Map(
        "sha" -> "${{github.sha}}",
        "body" ->
          """
            |**\uD83D\uDE80 Jmh Benchmark:**
            |
            |- Current Branch:
            | ${{steps.set_output.outputs.body}}""".stripMargin
      )
    )
  )
)
  )

  def jmhRun(batchSize: Int) = groupedBenchmarks(batchSize).map(l =>
    WorkflowJob(
      id = s"run_jmh_benchmark_${l.head}",
      name = s"Jmh Benchmark ${l.head}",
      scalas = List(Scala213),
      steps = List(
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", s"v2"),
          Map(
            "path" -> "zio-http"
          )
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "setup-java", s"v2"),
          Map(
            "distribution" -> "temurin",
            "java-version" -> "8"
          )
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          commands = List("cd zio-http", s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt", s"rm -f ${l.head}.txt",s"cat > ${l.head}.txt") ++ sbtCommand(l),
          id = Some("run_benchmark"),
          name = Some("Run Benchmark")
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "upload-artifact", s"v3"),
          Map(
            "name" -> s"jmh_result_${l.head}",
            "path" -> s"${l.head}.txt"
          )
        )
      )
    )
  )

  def apply(batchSize: Int): Seq[WorkflowJob] = jmhRun(batchSize) ++ jmhPublish(batchSize)

}