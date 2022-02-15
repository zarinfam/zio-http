package zhttp.http.middleware.logging
import zhttp.http.{Request, Response}
import zhttp.http.middleware.logging.LogFormat.{HttpLogFmt, LogFmt}

import scala.concurrent.duration.Duration

object LogEncoder {

  trait Encoder[T] {
    def encode(logFormat: HttpLogFmt, request: Request, response: Option[Response], duration: Long): T
  }

  object JSonEncoder {

//    {
//      "request": {
//
//       }
//    }

    implicit val jsonEncoder: Encoder[String] =
      (logFormat: HttpLogFmt, request: Request, response: Option[Response], duration: Long) =>
        interpretAsJsonString(logFormatit, request, response, duration)

    private def interpretAsJsonString(
      logFormat: LogFmt,
      request: Request,
      response: Option[Response],
      duration: Long,
    ): String = {
      logFormat match {
        case LogFmt.And(left, right)          =>
          interpretAsJsonString(left, request, response, duration) ++ interpretAsJsonString(
            right,
            request,
            response,
            duration,
          )
        case LogFmt.ColorWrap(_)              => ""
        case LogFmt.TextRenderingWrapper(_)   => ""
        case LogFmt.TextWrappers(_)           => ""
        case LogFmt.TimeUnitWrapper(timeUnit) =>
          val value = Duration(duration, timeUnit)
          s"""
             |"Duration":"${value.toString()}"
             |""".stripMargin
        case LogFmt.Spaced(left, right)       =>
          interpretAsJsonString(left, request, response, duration) ++ interpretAsJsonString(
            right,
            request,
            response,
            duration,
          )
        case LogFmt.NewLine(left, right)      =>
          interpretAsJsonString(left, request, response, duration) ++ interpretAsJsonString(
            right,
            request,
            response,
            duration,
          )
        case LogFmt.Fixed(_)                  => ""
        case LogFmt.Text(value)               =>
          s"""
             |"$value":
             |""".stripMargin
      }

    }

  }

  object ZioLoggingColoredEncoder {
    implicit val zioLoggingColoredEncoder: Encoder[String] =
      (logFormat: LogFmt, request: Request, response: Option[Response], duration: Long) => ???
  }

  object HtmlEncoder {
    implicit val htmlEncoder: Encoder[String] =
      (logFormat: LogFmt, request: Request, response: Option[Response], duration: Long) => ???
  }

}
