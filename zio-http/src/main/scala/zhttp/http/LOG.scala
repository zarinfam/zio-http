package zhttp.http

//import zio.Task
//
//import java.time.ZonedDateTime
//import java.time.format.DateTimeFormatter
//import scala.io.AnsiColor

//trait Encoder[T] {
//  def encode(logFormat: LogFmt, request: Request, response: Option[Response], duration: Long): T
//}
//
//final case class LogChunk(req: Request, resp: Option[Response], startTime: Long, endTime: Long) {
//  def request: Request           = req
//  def response: Option[Response] = resp
//
//  def duration: Long                                      = endTime - startTime
//  def encode[A](fmt: LogFmt)(implicit enc: Encoder[A]): A = enc.encode(fmt, request, response, duration)
//
//}

//trait Logger[-Message, +Output] { self =>
//  def apply(msg: Message): Output
//
//}
//
//sealed trait ColoredLogger {
//  def RED: LogColor
//  def BLUE: LogColor
//  def YELLOW: LogColor
//  def CYAN: LogColor
//  def GREEN: LogColor
//  def MAGENTA: LogColor
//  def WHITE: LogColor
//  def RESET: LogColor
//  def BOLD: LogColor
//  def UNDERLINED: LogColor
//  def BLINK: LogColor
//}
//
//final case class LogColor(private[zhttp] val color: String) extends AnyVal
//
//object ConsoleLogColor extends ColoredLogger {
//  val RED: LogColor        = LogColor(AnsiColor.RED)
//  val BLUE: LogColor       = LogColor(AnsiColor.BLUE)
//  val YELLOW: LogColor     = LogColor(AnsiColor.YELLOW)
//  val CYAN: LogColor       = LogColor(AnsiColor.CYAN)
//  val GREEN: LogColor      = LogColor(AnsiColor.GREEN)
//  val MAGENTA: LogColor    = LogColor(AnsiColor.MAGENTA)
//  val WHITE: LogColor      = LogColor(AnsiColor.WHITE)
//  val RESET: LogColor      = LogColor(AnsiColor.RESET)
//  val BOLD: LogColor       = LogColor(AnsiColor.BOLD)
//  val UNDERLINED: LogColor = LogColor(AnsiColor.UNDERLINED)
//  val BLINK: LogColor      = LogColor(AnsiColor.BLINK)
//}
//
//object HtmlColor extends ColoredLogger {
//  val RED: LogColor        = LogColor("#ff0000")
//  val BLUE: LogColor       = LogColor("#0000ff")
//  val YELLOW: LogColor     = LogColor("#ffff00")
//  val CYAN: LogColor       = LogColor("#00ffff")
//  val GREEN: LogColor      = LogColor("#00ff00")
//  val MAGENTA: LogColor    = LogColor("#ff00ff")
//  val WHITE: LogColor      = LogColor("#ffffff")
//  val RESET: LogColor      = LogColor("#000000")
//  val BOLD: LogColor       = LogColor("<b>")
//  val UNDERLINED: LogColor = LogColor("<ins>")
//  val BLINK: LogColor      = LogColor("<em>") // as blinking text is not supported type
//}
//
//sealed trait LogAppender { self =>
//
//  /**
//   * Appends a numeric value to the log.
//   */
//  def appendNumeric[A](numeric: A): Unit
//
//  /**
//   * Appends unstructured text to the log.
//   */
//  def appendText(text: String): Unit
//
//  /**
//   * Appends a key/value string pair to the log.
//   */
//  final def appendKeyValue(key: String, value: String): Unit = appendKeyValue(key, _.appendText(value))
//
//  /**
//   * Appends a key/value pair, with the value it created with the log appender.
//   */
//  final def appendKeyValue(key: String, appendValue: LogAppender => Unit): Unit = {
//    openKey()
//    try appendText(key)
//    finally closeKeyOpenValue()
//    try appendValue(self)
//    finally closeValue()
//  }
//
//  /**
//   * Marks the close of a key for a key/value pair, and the opening of the value.
//   */
//  def closeKeyOpenValue(): Unit
//
//  /**
//   * Marks the close of the value of a key/value pair.
//   */
//  def closeValue(): Unit
//
//  /**
//   * Marks the open of the key.
//   */
//  def openKey(): Unit
//}
//
//object LogAppender {
//
//  def unstructured(textAppender: String => Any): LogAppender = new LogAppender { self =>
//    def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)
//
//    def appendText(text: String): Unit = { textAppender(text); () }
//
//    def closeKeyOpenValue(): Unit = appendText("=")
//
//    def closeValue(): Unit = ()
//
//    def openKey(): Unit = ()
//  }
//
//}
//
//sealed trait LogFmt { self =>
//
//  import LogFmt.text
//
//  private[zhttp] def unsafeFormat(builder: LogAppender): Logger[String, Unit]
//
//  def +(other: LogFmt): LogFmt = concat(other)
//
//  /**
//   * Returns a new log format which concats both formats together with a space character between them.
//   */
//  final def |-|(other: LogFmt): LogFmt =
//    self + text(" ") + other
//
//  /**
//   * The alphanumeric version of the `|-|` operator.
//   */
//  final def spaced(other: LogFmt): LogFmt =
//    this |-| other
//
//  /**
//   * Returns a new log format that produces the same output as this one, but with the specified color applied.
//   */
//  final def color[T <: ColoredLogger](color: LogColor, t: T): LogFmt =
//    text(color.color) + self + text(t.RESET.color)
//
//  /**
//   * The alphanumeric version of the `+` operator.
//   */
//  final def concat(other: LogFmt): LogFmt =
//    this + other
//
//  /**
//   * Returns a new log format that produces the same as this one, but with a space-padded, fixed-width output. Be
//   * careful using this operator, as it destroys all structure, resulting in purely textual log output.
//   */
//  final def fixed(size: Int): LogFmt =
//    LogFmt.make { builder =>
//      val tempBuilder = new StringBuilder
//      val append      = LogAppender.unstructured { (line: String) =>
//        tempBuilder.append(line)
//        ()
//      }
//      self.unsafeFormat(append)
//
//      val messageSize = tempBuilder.size
//      if (messageSize < size) {
//        builder.appendText(tempBuilder.take(size).appendAll(Array.fill(size - messageSize)(' ')).toString())
//      } else {
//        builder.appendText(tempBuilder.take(size).toString())
//      }
//    }
//
//  /**
//   * Returns a new log format that produces the same as this one, except that log levels are colored according to the
//   * specified mapping.
//   */
//  final def highlight[T <: ColoredLogger](color: LogColor, t: T): LogFmt =
//    LogFmt.make { builder =>
//      builder.appendText(color.color)
//      try self.unsafeFormat(builder)
//      finally builder.appendText(t.RESET.color)
//    }
//
//}
//
//object LogFmt {
//
//  private val NewLine                          = System.lineSeparator()
//  def make(format: LogAppender => Any): LogFmt = new LogFmt {
//    override private[zhttp] def unsafeFormat(builder: LogAppender) = _ => format(builder): Unit
//  }
//
//  def text(value: => String): LogFmt =
//    LogFmt.make { builder =>
//      builder.appendText(value)
//    }
//
//  val newLine: LogFmt = text(NewLine)
//
//  val quote: LogFmt = text("\"")
//
//  def quoted(inner: LogFmt): LogFmt = quote + inner + quote
//
//  val timestamp: LogFmt = timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
//
//  def timestamp(formatter: => DateTimeFormatter): LogFmt =
//    text {
//      val now = ZonedDateTime.now()
//      formatter.format(now)
//    }
//
//  def label(label: => String, value: LogFmt): LogFmt =
//    LogFmt.make { builder =>
//      builder.openKey()
//      try builder.appendText(label)
//      finally builder.closeKeyOpenValue()
//
//      try value.unsafeFormat(builder)
//      finally builder.closeValue()
//    }
//
//  def bracketed(inner: LogFmt): LogFmt =
//    bracketStart + inner + bracketEnd
//
//  val bracketStart: LogFmt = text("[")
//
//  val bracketEnd: LogFmt = text("]")
//
//  // configure how to represent the body
//  def method(name: String, fmt: LogFmt): LogFmt = label(name, fmt)
//
//  def url(name: String, fmt: LogFmt): LogFmt = label(name, fmt)
//
//  def body(name: String, fmt: LogFmt): LogFmt = label(name, fmt)
//
//  def headers(name: String, fmt: LogFmt): LogFmt = label(name, fmt)
//
//  def header(name: String, fmt: LogFmt): LogFmt = label(name, fmt)
//
//  def status(name: String, fmt: LogFmt): LogFmt = label(name, fmt)
//
//  def request(name: String, fmt: LogFmt): LogFmt = label(name, fmt)
//
//  def response(name: String, fmt: LogFmt): LogFmt = label(name, fmt)
//
//  val defaultRequest: LogFmt =
//    method("", ???).fixed(8) |-|
//      url("", ???).fixed(128) |-|
//      headers("", ???).fixed(32) |-|
//      body("", ???).color(ConsoleLogColor.RED, ConsoleLogColor.RESET)
//
//  val defaultResponse = status("", ???).color(ConsoleLogColor.RED, ConsoleLogColor.RESET) |-| newLine |-|
//    body("", ???).color(ConsoleLogColor.CYAN, ConsoleLogColor.RESET)
//
//  val default: LogFmt = defaultRequest + defaultResponse
//  // val colored: LogFmt = ???
//
////    label("timestamp", timestamp.fixed(32)).color(LogColor.BLUE) |-|
////      label("level", level).highlight |-|
////      label("thread", fiberId).color(LogColor.WHITE) |-|
////      label("message", quoted(line)).highlight
//
//}

//object LOG {
//  // import LogFmt._
//
//  // val DefaultLogConfig = request(method ++ url ++ headers ++ body) ++ response(status ++ headers ++ body) ++ duration
//}

//sealed trait LogFmt { self =>
//
//  def ++(that: LogFmt): LogFmt = self combine that
//
//  def combine(that: LogFmt): LogFmt = LogFmt.And(self, that)
//
//  def run(response: Response, start: Long, end: Long): Task[String] =
//    response.getBodyAsByteBuf
//      .map(_.toString(HTTP_CHARSET))
//      .map { body =>
//        execute(
//          false,
//          self,
//          method = None,
//          url = None,
//          response.headers,
//          body,
//          Some(response.status),
//          Some((end - start)),
//        )
//      }
//      .map(r => s"Response: $r")
//
//  def run(request: Request): Task[String] = {
//    request.getBodyAsString.map { body =>
//      execute(
//        true,
//        self,
//        Some(request.method),
//        Some(request.url),
//        request.getHeaders,
//        body,
//        status = None,
//        duration = None,
//      )
//    }.map(r => s"Request: $r")
//
//  }
//
//  private def execute(
//    isRequest: Boolean,
//    logFmt: LogFmt,
//    method: Option[Method],
//    url: Option[URL],
//    headers: Headers,
//    body: String,
//    status: Option[Status],
//    duration: Option[Long],
//  ): String = {
//    logFmt match {
//      case LogFmt.And(left, right) =>
//        execute(isRequest, left, method, url, headers, body, status, duration) ++
//          execute(isRequest, right, method, url, headers, body, status, duration)
//
//      case LogFmt.Method if method.isDefined     => s" Method: ${method.get},"
//      case LogFmt.Url if url.isDefined           => s" Url: ${url.get.path.toString},"
//      case LogFmt.Status if status.isDefined     => s" Status: ${status.get},"
//      case LogFmt.Duration if duration.isDefined => s" Duration: ${duration.get}ms"
//      case LogFmt.Body(limit)                    => s" Body: ${body.take(limit)}, "
//      case LogFmt.Request(fmt) if isRequest   => execute(isRequest, fmt, method, url, headers, body, status, duration)
//      case LogFmt.Response(fmt) if !isRequest => execute(isRequest, fmt, method, url, headers, body, status, duration)
//      case LogFmt.Headers(filter)             =>
//        s" Headers: ${LogFmt.stringifyHeaders(filter(headers)).mkString}"
//      case _                                  => ""
//    }
//  }
//
//}
//
//object LogFmt {
//
//  private val NewLine = System.lineSeparator()
//
//  final case class And(left: LogFmt, right: LogFmt)                          extends LogFmt
//  case object Status                                                         extends LogFmt
//  case object Method                                                         extends LogFmt
//  case object Url                                                            extends LogFmt
//  case object Duration                                                       extends LogFmt
//  final case class Body(limit: Int)                                          extends LogFmt
//  final case class Request(fmt: LogFmt)                                      extends LogFmt
//  final case class Response(fmt: LogFmt)                                     extends LogFmt
//  final case class Headers(filter: zhttp.http.Headers => zhttp.http.Headers) extends LogFmt
//
//  def status: LogFmt                                                               = Status
//  def method: LogFmt                                                               = Method
//  def url: LogFmt                                                                  = Url
//  def body(limit: Int = 128)                                                       = Body(limit)
//  def body                                                                         = Body(128)
//  def duration: LogFmt                                                             = Duration
//  def request(logFmt: LogFmt): LogFmt                                              = Request(logFmt)
//  def response(logFmt: LogFmt): LogFmt                                             = Response(logFmt)
//  def headers(filter: zhttp.http.Headers => zhttp.http.Headers = identity): LogFmt =
//    Headers(filter)
//
//  def headers = Headers(identity)
//
//  private def stringifyHeaders(headers: zhttp.http.Headers): List[String] = headers.toList.map { case (name, value) =>
//    s" $name = $value,"
//  }
//}
