package net.kemitix.eip.zio

import zio.{DefaultRuntime, ZIO}
import zio.clock.Clock
import org.scalatest.freespec.AnyFreeSpec

class MessageTest extends AnyFreeSpec {

  "create a message" in {
    val message: ZIO[Clock, Nothing, Message[String]] = Message.create("body")
    val result: Either[Throwable, Message[String]] =
      new DefaultRuntime {}.unsafeRunSync(message).toEither
    assertResult(Right("body"))(result.map(_.body))
  }

  "create a message without a Clock" in {
    val message: ZIO[Any, Nothing, Message[String]] = Message.withBody("body")
    val result: Either[Throwable, Message[String]] =
      new DefaultRuntime {}.unsafeRunSync(message).toEither
    assertResult(Right("body"))(result.map(_.body))
  }

  "message body is covariant" in {
    // compilation only test
    sealed trait MyBody
    def foo(m: Message[MyBody]): Unit = ()
    case class FirstBody()  extends MyBody
    case class SecondBody() extends MyBody
    val message1 = Message(Map.empty, FirstBody())
    val message2 = Message(Map.empty, SecondBody())
    foo(message1)
    foo(message2)
  }

}
