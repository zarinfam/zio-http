package zio.http

import zio._

trait ResponseEncoder {

  def encode(response: Response)(implicit trace: Trace): Task[ResponseEncoder.EncodedResponse]

}

object ResponseEncoder {

  trait EncodedResponse extends Any
}
