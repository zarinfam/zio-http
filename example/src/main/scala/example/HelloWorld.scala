package example

import zhttp.http._
import zhttp.service.Server
import zio._
object HelloWorld extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "a" => Response.text("a")
    case Method.GET -> !! / "a" / ! => Response.text(s"Hello World! ${(!! / "a" / ! ).encode}")
    case Method.GET -> !! / a  => Response.text(s"${a}")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
