package net.kemitix.eip.zio

import zio.{IO, UIO, ZIO, stream}

object MessageChannel {
  type Channel           = zio.stream.Stream[Unit, Unit]
  type Callback[Message] = IO[Option[Unit], Message] => Unit
  type Sender[Message]   = Callback[Message] => UIO[Unit]
  type Receiver[Message] = Message => UIO[Unit]
  def endChannel[Message]: Callback[Message] => Unit =
    cb => cb(ZIO.fail(None))

  /**
    * Use Messaging to make asynchronous calls or transfer documents/events.
    *
    * https://www.enterpriseintegrationpatterns.com/patterns/messaging/PointToPointChannel.html
    *
    * Creates a Message Channel from an asynchronous callback that can be called multiple times
    * by the sender to send a Message to the receiver.
    *
    * The sender is a callback that returns an effect. It will be forked onto a new Fiber.
    *
    * The sender sends Messages using the callback, wrapping the message within a successful ZIO.
    *
    * The channel can be shutdown be a call to [[MessageChannel.endChannel]].
    *
    * Unlike the EIP Message Channel, there can only be a single receiver.
    *
    * @param sender A callback that returns an effect
    * @param receiver A function that will be called for each Message
    * @tparam Message The type of the Message
    * @return a Channel
    */
  def pointToPoint[Message](sender: Sender[Message])(
      receiver: Receiver[Message]
  ): Channel = {
    pointToPointPar(1)(sender)(receiver)
  }

  /**
    * See [[MessageChannel.pointToPoint()]]
    *
    * Executes 'n' receivers running in parallel.
    *
    * @param n the number of parallel receivers
    */
  def pointToPointPar[Message](n: Int)(sender: Sender[Message])(
      receiver: Receiver[Message]
  ): Channel = {
    zio.stream.Stream
      .effectAsyncM((cb: Callback[Message]) => sender(cb).fork.unit)
      .mapMPar(n)(receiver)
  }
}
