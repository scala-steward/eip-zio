package net.kemitix.eip.zio

import zio.{IO, RIO, UIO, ZIO, stream}

object MessageChannel {
  type Callback[Message]     = IO[Option[Unit], Message] => Unit
  type Channel               = zio.stream.Stream[Unit, Unit]
  type Sender[Message]       = Callback[Message] => UIO[Unit]
  type Receiver[Message]     = Message => UIO[Unit]
  type CallbackR[R, Message] = ZIO[R, Option[Unit], Message] => Unit
  type ChannelR[R]           = zio.stream.ZStream[R, Unit, Unit]
  type SenderR[R, Message]   = CallbackR[R, Message] => ZIO[R, Nothing, Unit]
  type ReceiverR[R, Message] = Message => ZIO[R, Nothing, Unit]
  def endChannel[Message]: Callback[Message] => Unit =
    cb => cb(ZIO.fail(None))
  def send[Message](cb: Callback[Message])(message: Message): Unit =
    cb(ZIO.succeed(message))
  def sendR[RSend, Message](cb: CallbackR[RSend, Message])(
      message: Message): Unit =
    cb(ZIO.succeed(message))

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
    * @tparam RSend The environment for the sender
    * @tparam RReceive The environment for the receiver
    * @return a Channel
    */
  def pointToPointR[RSend, RReceive, Message](sender: SenderR[RSend, Message])(
      receiver: ReceiverR[RReceive, Message]
  ): ChannelR[RSend with RReceive] = {
    pointToPointRPar[RSend, RReceive, Message](1)(sender)(receiver)
  }
  def pointToPoint[Message](sender: Sender[Message])(
      receiver: Receiver[Message]
  ): Channel = {
    pointToPointPar(1)(sender)(receiver)
  }

  /**
    * See [[MessageChannel.pointToPointR()]]
    *
    * Executes 'n' receivers running in parallel.
    *
    * @param n the number of parallel receivers
    */
  def pointToPointRPar[RSend, RReceive, Message](n: Int)(
      sender: SenderR[RSend, Message])(
      receiver: ReceiverR[RReceive, Message]
  ): ChannelR[RSend with RReceive] = {
    zio.stream.ZStream
      .effectAsyncM((cb: CallbackR[RSend, Message]) => sender(cb).fork.unit)
      .mapMPar(n)(receiver)
  }
  def pointToPointPar[Message](n: Int)(sender: Sender[Message])(
      receiver: Receiver[Message]
  ): Channel = {
    zio.stream.ZStream
      .effectAsyncM((cb: Callback[Message]) => sender(cb).fork.unit)
      .mapMPar(n)(receiver)
  }
}
