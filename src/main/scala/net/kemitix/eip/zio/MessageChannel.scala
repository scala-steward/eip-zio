package net.kemitix.eip.zio

import zio.{UIO, ZIO}

/**
  * A channel connecting a Sender and Receiver of Messages of type Body.
  */
object MessageChannel {

  // channels that can abort with errors from the Sender
  type EChannel[R, E, Body] = ZIO[R, Option[E], Message[Body]] => Unit
  type EChannelHandle[R, E] = zio.stream.ZStream[R, E, Unit]
  type ESender[R, E, Body]  = EChannel[R, E, Body] => ZIO[R, E, Unit]

  // channels that can't 'fail' (Uninterruptable)
  type UChannel[R, Body]  = EChannel[R, Nothing, Body]
  type UChannelHandle[R]  = EChannelHandle[R, Nothing]
  type USender[R, Body]   = ESender[R, Nothing, Body]
  type UReceiver[R, Body] = Message[Body] => ZIO[R, Nothing, Unit]

  // Aliases for uninterruptable channels - the proposed default
  type Channel[R, Body]  = UChannel[R, Body]
  type ChannelHandle[R]  = UChannelHandle[R]
  type Sender[R, Body]   = USender[R, Body]
  type Receiver[R, Body] = UReceiver[R, Body]

  type Forwarder[R, BodyIn, BodyOut] =
    Message[BodyIn] => ZIO[R, Nothing, Message[BodyOut]]

  object Syntax {

    implicit class ExtendUSender[RSend, Body](sender: USender[RSend, Body]) {
      def =>>[RReceive](receiver: MessageChannel.UReceiver[RReceive, Body])
        : EChannelHandle[RSend with RReceive, Nothing] =
        MessageChannel.pointToPoint(sender)(receiver)
    }

    implicit class ExtendESender[RSend, E, Body](
        sender: ESender[RSend, E, Body]) {
      def =>>[RReceive](receiver: MessageChannel.Receiver[RReceive, Body])
        : EChannelHandle[RSend with RReceive, E] =
        MessageChannel.pointToPoint(sender)(receiver)
    }

  }

  // Normal shutdown of a channel
  def endChannel[Body](channel: Channel[Any, Body]): UIO[Unit] =
    UIO(channel(ZIO.fail(None)))

  // Abnormal termination of a channel
  def abortChannel[R, E, Body](channel: EChannel[R, E, Body])(
      error: E): UIO[Unit] = UIO(channel(ZIO.fail(Some(error))))

  // Dispatch a message into the channel from the Sender
  def send[RSend, Body](channel: Channel[RSend, _ >: Body])(
      message: Message[Body]): UIO[Unit] =
    UIO(channel(ZIO.succeed(message)))

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
    * @tparam ESend The expected error type that can be returned by the Sender
    * @return a Channel
    */
  def pointToPoint[R, RSend >: R, RReceive >: R, E, ESend <: E, Body](
      sender: ESender[RSend, E, Body])(
      receiver: UReceiver[RReceive, Body]
  ): EChannelHandle[RSend with RReceive, E] = {
    pointToPointPar(1)(sender)(receiver)
  }

  /**
    * See [[MessageChannel.pointToPoint]]
    *
    * Executes 'n' receivers running in parallel.
    *
    * @param n the number of parallel receivers
    */
  def pointToPointPar[R, RSend >: R, RReceive >: R, E, ESend <: E, Body](
      n: Int)(sender: ESender[RSend, ESend, Body])(
      receiver: UReceiver[RReceive, Body]): EChannelHandle[R, E] =
    zio.stream.ZStream
      .effectAsyncM((channel: EChannel[R, E, Body]) =>
        sender(channel).fork.unit)
      .mapMPar(n)(receiver)

}
