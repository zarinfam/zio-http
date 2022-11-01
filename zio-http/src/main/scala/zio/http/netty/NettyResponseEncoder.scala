package zio.http.netty

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultFullHttpResponse, DefaultHttpResponse, HttpResponse}
import zio._
import zio.http._

final case class NettyResponseEncoder() extends ResponseEncoder {

  def encode(response: Response)(implicit trace: Trace): Task[ResponseEncoder.EncodedResponse] = {
    doEncode(response)
    // val encodedResponse = response.encodedResponse.get

    // (response.frozen, encodedResponse) match {
    //   case (true, Some(encoded)) => ZIO.succeed(encoded)

    //   case (true, None) =>
    //     for {
    //       encoded <- doEncode(response)
    //       _       <- ZIO.succeed(response.withEncodedResponse(Some(encoded)))
    //     } yield encoded
    //   case (false, _)   => doEncode(response)
    // }
  }

  private def doEncode(response: Response)(implicit trace: Trace): Task[ResponseEncoder.EncodedResponse] = {
    val body = response.body
    for {
      content <- if (body.isComplete) body.asChunk.map(Some(_)) else ZIO.succeed(None)
      res     <-
        ZIO.attempt {
          import io.netty.handler.codec.http._
          val jHeaders         = response.headers.encode
          val hasContentLength = jHeaders.contains(HttpHeaderNames.CONTENT_LENGTH)
          content.map(chunks => Unpooled.wrappedBuffer(chunks.toArray)) match {
            case Some(jContent) =>
              val jResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, response.status.asJava, jContent, false)

              // TODO: Unit test for this
              // Client can't handle chunked responses and currently treats them as a FullHttpResponse.
              // Due to this client limitation it is not possible to write a unit-test for this.
              // Alternative would be to use sttp client for this use-case.
              if (!hasContentLength) jHeaders.set(HttpHeaderNames.CONTENT_LENGTH, jContent.readableBytes())
              jResponse.headers().add(jHeaders)
              jResponse
            case None           =>
              if (!hasContentLength) jHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
              new DefaultHttpResponse(HttpVersion.HTTP_1_1, response.status.asJava, jHeaders)
          }
        }
    } yield NettyResponseEncoder.NettyEncodedResponse(res)
  }

}

object NettyResponseEncoder {

  val layer: ULayer[ResponseEncoder] = ZLayer.succeed(NettyResponseEncoder())

  final case class NettyEncodedResponse private[zio] (jResponse: HttpResponse)
      extends AnyVal
      with ResponseEncoder.EncodedResponse
}
