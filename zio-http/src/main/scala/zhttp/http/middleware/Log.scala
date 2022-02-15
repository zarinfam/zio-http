package zhttp.http.middleware
import zhttp.http.middleware.logging.LogFormat.LogChunk
import zhttp.http.{Patch, Request, Response}
import zio.ZIO

private[zhttp] trait Log {

  /**
   * Provides a logging middleware
   */

  def logRequest[R0](
    logger: LogChunk => ZIO[R0, Throwable, Unit],
    request: Request,
    startTime: Long,
  ): ZIO[R0, Throwable, LogChunk] = ???
//  {
//    val logData = logFmt.run(request)
//    logData.flatMap { log =>
//      logger(log, LogLevel.Info).as(startTime)
//    }
//
//  }

  def logResponse[R0](
    logger: LogChunk => ZIO[R0, Throwable, Unit],
    response: Response,
    startTime: Long,
    endTime: Long,
  ): ZIO[R0, Option[Throwable], Patch] = ???
//  {
//    val logData =
//      logFmt.run(response, startTime, endTime)
//    if (response.status.asJava.code() > 499) {
//      logData.flatMap { log =>
//        logger(log, LogLevel.Error)
//      }.mapBoth(e => Some(e), _ => Patch.empty)
//    } else {
//      logData.flatMap { log =>
//        logger(log, LogLevel.Info)
//      }.mapBoth(e => Some(e), _ => Patch.empty)
//    }
//  }

}
