package zhttp.http.middleware.logging

import zhttp.http.middleware.logging.LogEncoder.Encoder
import zhttp.http.{Headers, Request, Response}

import java.util.concurrent.TimeUnit

object LogFormat {

  final case class LogChunk(req: Request, resp: Option[Response], startTime: Long, endTime: Long) {
    def request: Request           = req
    def response: Option[Response] = resp

    def duration: Long                                      = endTime - startTime
    def encode[A](fmt: LogFmt)(implicit enc: Encoder[A]): A = enc.encode(fmt, request, response, duration)

  }

  sealed trait Color
  object Color         {
    final case object RED     extends Color
    final case object BLUE    extends Color
    final case object YELLOW  extends Color
    final case object CYAN    extends Color
    final case object GREEN   extends Color
    final case object MAGENTA extends Color
    final case object WHITE   extends Color
    final case object RESET   extends Color
    final case object DEFAULT extends Color
  }
  sealed trait TextRendering
  object TextRendering {
    final case object BOLD       extends TextRendering
    final case object UNDERLINED extends TextRendering
    final case object BLINK      extends TextRendering
    final case object ITALIAN    extends TextRendering
    final case object DEFAULT    extends TextRendering
  }
  sealed trait TextWrapper
  object TextWrapper   {
    final case object BRACKET extends TextWrapper
    final case object QUOTED  extends TextWrapper
    final case object EMPTY   extends TextWrapper
  }

  sealed trait LogFmt { self =>
    import LogFmt._
    def <+>(that: LogFmt): LogFmt     = self combine that
    def combine(that: LogFmt): LogFmt = LogFmt.And(self, that)

    final def color(color: Color): LogFmt                 = ColorWrap(color)
    final def textRendering(textRendering: TextRendering) = TextRenderingWrapper(textRendering)

    final def wrap(wrapper: TextWrapper) = TextWrappers(wrapper)

    final def fixed(size: Int): LogFmt = Fixed(size)

    final def |-|(other: LogFmt): LogFmt = Spaced(self, other)

    final def \\(other: LogFmt): LogFmt = NewLine(self, other)

    final def unit(timeUnit: TimeUnit): LogFmt = TimeUnitWrapper(timeUnit)

    final def trim: LogFmt = Trim(self)

  }

  object LogFmt {
    final case class And(left: LogFmt, right: LogFmt) extends LogFmt

    final case class ColorWrap(color: Color) extends LogFmt

    final case class TextRenderingWrapper(textRendering: TextRendering) extends LogFmt

    final case class TextWrappers(wrapper: TextWrapper) extends LogFmt

    final case class TimeUnitWrapper(timeUnit: TimeUnit) extends LogFmt

    final case class Spaced(left: LogFmt, right: LogFmt) extends LogFmt

    final case class NewLine(left: LogFmt, right: LogFmt) extends LogFmt

    final case class Fixed(size: Int) extends LogFmt

    final case class Text(value: String) extends LogFmt

    final case class Trim(logFmt: LogFmt) extends LogFmt

    def text(value: String): LogFmt = Text(value)

  }

  sealed trait HttpLogFmt {
    self =>
    def <+>(that: HttpLogFmt): HttpLogFmt = self combine that

    def combine(that: HttpLogFmt): HttpLogFmt = HttpLogFmt.And(self, that)
  }

  object HttpLogFmt {
    final case class And(left: HttpLogFmt, right: HttpLogFmt) extends HttpLogFmt

    final case class Status(fmt: LogFmt) extends HttpLogFmt

    final case class Method(fmt: LogFmt) extends HttpLogFmt

    final case class Url(fmt: LogFmt) extends HttpLogFmt

    final case class Duration(fmt: LogFmt) extends HttpLogFmt

    final case class RequestBody(limit: Int, fmt: LogFmt) extends HttpLogFmt

    final case class ResponseBody(limit: Int, fmt: LogFmt) extends HttpLogFmt

    final case class Request(fmt: LogFmt) extends HttpLogFmt

    final case class Response(fmt: LogFmt) extends HttpLogFmt

    final case class RequestHeaders(filter: Headers => Headers, fmt: LogFmt) extends HttpLogFmt

    final case class ResponseHeaders(filter: Headers => Headers, fmt: LogFmt) extends HttpLogFmt

    def duration(fmt: LogFmt): HttpLogFmt = Duration(fmt)

    def responseHeaders(filter: Headers => Headers = identity, fmt: LogFmt): HttpLogFmt = ResponseHeaders(filter, fmt)

    def requestHeaders(filter: Headers => Headers = identity, fmt: LogFmt): HttpLogFmt =
      RequestHeaders(filter, fmt)

    def requestHeaders(fmt: LogFmt): HttpLogFmt = RequestHeaders(identity, fmt)

    def responseHeaders(fmt: LogFmt): HttpLogFmt = ResponseHeaders(identity, fmt)

    def status(fmt: LogFmt): HttpLogFmt = Status(fmt)

    def method(fmt: LogFmt): HttpLogFmt = Method(fmt)

    def url(fmt: LogFmt): HttpLogFmt = Url(fmt)

    def requestBody(fmt: LogFmt, limit: Int = 128): HttpLogFmt = RequestBody(limit, fmt)

    def responseBody(fmt: LogFmt, limit: Int = 128): HttpLogFmt = ResponseBody(limit, fmt)

  }

  object default {
    import zhttp.http.middleware.logging.LogFormat.LogFmt._
    import zhttp.http.middleware.logging.LogFormat.HttpLogFmt._

    val headersFmt = text("Headers").fixed(32).color(Color.GREEN)
    val methodFmt  = text("").color(Color.GREEN).fixed(16).textRendering(TextRendering.BOLD)
    val urlFmt     = text("").color(Color.CYAN).fixed(256)
    val bodyFmt    = text("Content:").color(Color.YELLOW).wrap(TextWrapper.BRACKET)
    val statusFmt  = text("").color(Color.RED).textRendering(TextRendering.BOLD)

    val requestFmt = method(methodFmt) <+> url(urlFmt) <+> requestHeaders(headersFmt) <+> requestBody(bodyFmt)

    val responseFmt = status(statusFmt) <+> responseHeaders(headersFmt) <+> responseBody(bodyFmt)

    val durationFmt = text("Duration:").color(Color.BLUE).unit(TimeUnit.SECONDS)

    val defaultLogFmt = requestFmt <+> responseFmt <+> duration(durationFmt)
  }

}
