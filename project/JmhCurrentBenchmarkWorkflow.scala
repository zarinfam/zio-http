import BuildHelper.{JmhVersion, Scala213}
import sbt.nio.file.FileTreeView
import sbt.{**, Glob, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhCurrentBenchmarkWorkflow {

  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""
  val scalaSources: PathFilter = ** / "*.scala"
  val files = FileTreeView.default.list(Glob("./zio-http-benchmarks/src/main/scala/zhttp.benchmarks/**"), scalaSources)

  def getStr = files.map(file => {
    val path = file._1.toString
    path.replaceAll("^.*[\\/\\\\]", "").replaceAll(".scala", "")
  }).sorted
  println(getStr)

  def lists1(list: Seq[String]) = list.map(str =>
    s"""sbt -no-colors -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" | grep "thrpt" >> ../${list.head}.txt""".stripMargin)

  def list2 = getStr.grouped(4).toList
  val need = list2.flatMap((l: Seq[String]) => List(s"run_jmh_benchmark_${l.head}"))
  println(need)
  println(list2)

  val comment =    Seq(WorkflowJob(
  runsOnExtraLabels = List("zio-http"),
  id = "jmh_publish",
  name = "Jmh Publish",
  oses = List("centos"),
  scalas = List(Scala213),
  needs =  need,
  steps = list2.map(l => {
    WorkflowStep.Use(
      ref = UseRef.Public("actions", "download-artifact", "v3"),
      Map(
        "name" -> s"jmh_result_${l.head}"
      )
    )
  }) ++ Seq(
    WorkflowStep.Run(
      commands = List(
        s"""rm -f body.txt
           |cat > body.txt
           |echo "::set-output name=res::$$(echo "$$(<body.txt)")"""".stripMargin),
      id = Some("create_body"),
      name = Some("create_body")
    )
  ) ++ list2.map( l => {
    WorkflowStep.Run(
      commands = List(
        s"""while IFS= read -r line; do
           |   IFS=' ' read -ra PARSED_RESULT <<< "$$line"
           |   B_VALUE=$$(echo "$${PARSED_RESULT[1]}": "$${PARSED_RESULT[4]}" ops/sec"")
           |   echo $$B_VALUE >> body.txt
           | done < ${l.head}.txt""".stripMargin),
      id = Some(s"echo_value_${l.head}"),
      name = Some(s"echo_value_${l.head}")
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
      id = Some("echo_value"),
      name = Some("echo_value")
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
            | ${{steps.echo_value.outputs.body}}""".stripMargin,
      ),
    )
  ),
),
)

  def jmhBenchmark() = list2.map(l =>
    WorkflowJob(
      id = s"run_jmh_benchmark_${l.head}",
      name = s"Jmh Benchmark_${l.head}",
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
            commands = List("cd zio-http", s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt", s"rm -f ${l.head}.txt",s"cat > ${l.head}.txt") ++ lists1(l),
            id = Some("run_benchmark"),
            name = Some("Run Benchmark"),
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
  ) ++ comment


  def apply(): Seq[WorkflowJob] = jmhBenchmark()

}