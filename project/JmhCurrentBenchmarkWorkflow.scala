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

  def jmhPublish(batchSize: Int) = Seq(WorkflowJob(
  id = "jmh_publish",
  name = "Jmh Publish",
  scalas = List(Scala213),
  needs =  dependencies(batchSize),
  steps = groupedBenchmarks(batchSize).flatMap(l => {
    Seq(
    WorkflowStep.Use(
      ref = UseRef.Public("actions", "download-artifact", "v3"),
      Map(
        "name" -> s"jmh_result_Current_${l.head}"
      )
    ),
    WorkflowStep.Use(
      ref = UseRef.Public("actions", "download-artifact", "v3"),
      Map(
        "name" -> s"jmh_result_Main_${l.head}"
      )
    )
    )
  }) ++ Seq(
    WorkflowStep.Run(
      commands = List(
        """cat > body2.txt
        |cat > body2.txt
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
           |   echo $$B_VALUE >> body1.txt
           | done < Current_${l.head}.txt
           |while IFS= read -r line; do
           |   IFS=' ' read -ra PARSED_RESULT <<< "$$line"
           |   B_VALUE=$$(echo "$${PARSED_RESULT[1]}": "$${PARSED_RESULT[4]}" ops/sec"")
           |   echo $$B_VALUE >> body2.txt
           | done < Main_${l.head}.txt """.stripMargin),
      id = Some(s"result_current_${l.head}"),
      name = Some(s"Result Current ${l.head}")
    )
  }) ++ Seq(
    WorkflowStep.Run(
      commands = List(
      """body1=$(cat body1.txt)
        | body1="${body1//'%'/'%25'}"
        | body1="${body1//$'\n'/'%0A'}"
        | body1="${body1//$'\r'/'%0D'}"
        | echo "$body1"
        | echo "::set-output name=body1::$(echo "$body1")"
        | body2=$(cat body2.txt)
        | body2="${body2//'%'/'%25'}"
        | body2="${body2//$'\n'/'%0A'}"
        | body2="${body2//$'\r'/'%0D'}"
        | echo "$body2"
        | echo "::set-output name=body2::$(echo "$body2")"
        | """.stripMargin
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
            |- **Current Branch**:
            | ${{steps.set_output.outputs.body1}}
            |
            |- **Main Branch**:
            | ${{steps.set_output.outputs.body2}}
            | """.stripMargin
      )
    )
  )
)
  )

  def jmhRun(batchSize: Int, branch: String = "Current") = groupedBenchmarks(batchSize).map(l => {
    val checkout = if(branch == "Current") "v2" else "main"
    WorkflowJob(
      id = s"run_jmh_benchmark_${branch}_${l.head}",
      name = s"Jmh Benchmark ${branch} ${l.head}",
      scalas = List(Scala213),
      steps = List(
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", s"$checkout"),
          Map(
            "path" -> "zio-http"
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