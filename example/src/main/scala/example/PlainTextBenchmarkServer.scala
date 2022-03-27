package example

import io.netty.util.AsciiString
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server, UServer}
import zio.{App, ExitCode, UIO, URIO}

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object Main extends App {

  private val plainTextMessage: String = "Hello, World!"
  private val jsonMessage: String      = """{"greetings": "Hello World!"}"""

  private val plaintextPath = "/plaintext"
  private val jsonPath      = "/json"

  private val STATIC_SERVER_NAME = AsciiString.cached("zio-http")

  private val frozenJsonResponse = Response
    .json(jsonMessage)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)
    .freeze

  private val frozenPlainTextResponse = Response
    .text(plainTextMessage)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)
    .freeze

  private def plainTextApp(response: Response) = Http.fromHExit(HExit.succeed(response)).whenPathEq(plaintextPath)

  private def jsonApp(json: Response) = Http.fromHExit(HExit.succeed(json)).whenPathEq(jsonPath)

  private def app = for {
    plainTextResponse <- frozenPlainTextResponse
    jsonResponse      <- frozenJsonResponse
  } yield plainTextApp(plainTextResponse) ++ jsonApp(jsonResponse)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    app
      .flatMap(server(_).make.useForever)
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(8))
      .exitCode

  private final def leakDetectionLevel(level: String): UServer = level match {
    case "disabled" => Server.disableLeakDetection
    case "simple"   => Server.simpleLeakDetection
    case "advanced" => Server.advancedLeakDetection
    case "paranoid" => Server.paranoidLeakDetection
  }

  private def server(app: HttpApp[Any, Nothing]) = {
    zio.system.envs.map { envs =>
      val server = Server.port(8080) ++
        Server.app(app) ++
        Server.error(_ => UIO.unit) ++
        leakDetectionLevel(envs.getOrElse("LEAK_DETECTION_LEVEL", "disabled"))

      server ++
        (if (envs.getOrElse("CONSOLIDATE_FLUSH", "true").toBoolean) Server.consolidateFlush else server) ++
        (if (envs.getOrElse("DISABLE_FLOW_CONTROL", "true").toBoolean) Server.disableFlowControl else server)
    }
  }

}
