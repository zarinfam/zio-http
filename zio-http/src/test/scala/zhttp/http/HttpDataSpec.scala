package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.util.AsciiString
import zhttp.http.HttpData.ByteBufConfig
import zio.stream.ZStream
import zio.test.Assertion.{anything, equalTo, isLeft, isSubtype}
import zio.test.TestAspect.timeout
import zio.test._
import zio.{Chunk, durationInt}

import java.io.File

object HttpDataSpec extends ZIOSpecDefault {

  override def spec =
    suite("HttpDataSpec") {

      val testFile = new File(getClass.getResource("/TestFile.txt").getPath)
      suite("outgoing")(
        suite("encode")(
          suite("fromStream") {
            test("success") {
              check(Gen.string) { payload =>
                val stringBuffer    = payload.getBytes(HTTP_CHARSET)
                val responseContent = ZStream.fromIterable(stringBuffer)
                val res             = HttpData.fromStream(responseContent).toByteBuf.map(_.toString(HTTP_CHARSET))
                assertZIO(res)(equalTo(payload))
              }
            }
          },
          suite("fromFile")(
            test("failure") {
              val res = HttpData.fromFile(throw new Error("Failure")).toByteBuf.either
              assertZIO(res)(isLeft(isSubtype[Error](anything)))
            },
            test("success") {
              lazy val file = testFile
              val res       = HttpData.fromFile(file).toByteBuf.map(_.toString(HTTP_CHARSET))
              assertZIO(res)(equalTo("abc\nfoo"))
            },
            test("success small chunk") {
              lazy val file = testFile
              val res       = HttpData.fromFile(file).toByteBuf(ByteBufConfig(3)).map(_.toString(HTTP_CHARSET))
              assertZIO(res)(equalTo("abc\nfoo"))
            },
          ),
        ),
        suite("asString")(test("multiple calls allowed") {
          val abc = "abc"
          val ctx = new EmbeddedChannel()
          val gen = Gen
            .fromIterable(
              Seq(
                HttpData.fromString(abc),
                HttpData.fromAsciiString(new AsciiString(abc)),
                HttpData.fromByteBuf(Unpooled.copiedBuffer(abc, HTTP_CHARSET)),
                HttpData.fromChunk(Chunk.fromArray(abc.getBytes(HTTP_CHARSET))),
                HttpData.fromStream(ZStream.fromIterable(abc.getBytes(HTTP_CHARSET))),
                HttpData.unsafeAsync {
                  _(ctx, new DefaultLastHttpContent(Unpooled.copiedBuffer(abc, HTTP_CHARSET)))
                },
              ),
            )
          checkAll(gen) { data =>
            for {
              string    <- data.asString().repeatN(10)
              byteArray <- data.toByteArray.repeatN(10)
            } yield assertTrue(
              string == abc,
              byteArray == abc.getBytes(),
            )
          }
        }),
      )
    } @@ timeout(10 seconds)
}
