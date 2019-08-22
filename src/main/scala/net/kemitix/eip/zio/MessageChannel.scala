package net.kemitix.eip.zio

import zio.{UIO, ZIO}

/**
  * A channel connecting a Sender and Receiver of Messages of type Body.
  */
object MessageChannel {
  type Channel[R, Body]  = ZIO[R, Option[Unit], Message[Body]] => Unit
  type ChannelHandle[R]  = zio.stream.ZStream[R, Unit, Unit]
  type Sender[R, Body]   = Channel[R, Body] => ZIO[R, Nothing, Unit]
  type Receiver[R, Body] = Message[Body] => ZIO[R, Nothing, Unit]
  def endChannel[Body](cb: Channel[Any, Body]): UIO[Unit] =
    UIO(cb(ZIO.fail(None)))
  def send[RSend, Body](cb: Channel[RSend, Body])(
      message: Message[Body]): UIO[Unit] =
    UIO(cb(ZIO.succeed(message)))

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
    * @tparam Body The type of the Message body
    * @tparam RSend The environment for the sender
    * @tparam RReceive The environment for the receiver
    * @return a Channel
    */
  def pointToPoint[RSend, RReceive, Body](sender: Sender[RSend, Body])(
      receiver: Receiver[RReceive, Body]
  ): ChannelHandle[RSend with RReceive] = {
    pointToPointPar[RSend, RReceive, Body](1)(sender)(receiver)
  }

  /**
    * See [[MessageChannel.pointToPoint]]
    *
    * Executes 'n' receivers running in parallel.
    *
    * @param n the number of parallel receivers
    */
  def pointToPointPar[RSend, RReceive, Body](n: Int)(
      sender: Sender[RSend, Body])(
      receiver: Receiver[RReceive, Body]
  ): ChannelHandle[RSend with RReceive] =
    zio.stream.ZStream
      .effectAsyncM((cb: Channel[RSend, Body]) => sender(cb).fork.unit)
      .mapMPar(n)(receiver)

}
