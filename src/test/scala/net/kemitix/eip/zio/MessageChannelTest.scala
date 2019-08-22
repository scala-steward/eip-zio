package net.kemitix.eip.zio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import net.kemitix.eip.zio.MessageChannel.ChannelHandle
import org.scalatest.FreeSpec
import org.scalatest.Matchers._
import zio.clock.Clock
import zio.console._
import zio.{DefaultRuntime, UIO, ZIO}

class MessageChannelTest extends FreeSpec {

  "Point-to-Point Message Channel" - {
    "receive messages asynchronously" in {
      val output = new AtomicReference[List[String]](List.empty[String])
      val latch  = new AtomicReference[CountDownLatch](new CountDownLatch(1))

      def putStr(s: String): ZIO[Console, Nothing, Unit] =
        for {
          _ <- putStrLn(s)
          _ <- UIO(output.updateAndGet(l => s :: l))
        } yield ()

      def sender: MessageChannel.Sender[Clock with Console, Int] =
        channel =>
          // use of latch is only to avoid tests being flaky
          // ensure receiver has completed
          // don't use them in your own code
          for {
            _ <- ZIO.foreach[Clock with Console, Nothing, Int, Unit](1 to 3) {
              counter =>
                for {
                  m <- Message.create(counter)
                  _ <- putStr("put " + counter.toString)
                  _ <- UIO(latch.set(new CountDownLatch(1)))
                  _ <- MessageChannel.send(channel)(m)
                  _ <- UIO(latch.get.await())
                } yield ()
            }
            _ <- MessageChannel.endChannel(channel)
          } yield ()

      def receiver: MessageChannel.Receiver[Console, Int] =
        message =>
          for {
            _ <- putStr("got " + message.body.toString)
            _ <- UIO(latch.get.countDown())
          } yield ()

      val program: ChannelHandle[Clock with Console] =
        MessageChannel.pointToPoint(sender)(receiver)

      new DefaultRuntime {}.unsafeRunSync(program.runDrain)

      val expected = List(
        "put 1",
        "got 1",
        "put 2",
        "got 2",
        "put 3",
        "got 3"
      )
      assertResult(expected)(output.get.reverse)
    }
    "multiple receivers" - {
      "consumed once and non-blocking" in {
        // messages are only processed by one receiver
        // messages being processed don't block other receivers
        val output = new AtomicReference[List[String]](List.empty[String])

        def putStr(s: String): ZIO[Console, Nothing, Unit] =
          for {
            _ <- putStrLn(s)
            _ <- UIO(output.updateAndGet(l => s :: l))
          } yield ()

        val nFibers = 3

        def sender: MessageChannel.Sender[Clock, Int] =
          channel =>
            for {
              _ <- ZIO.foreach[Clock, Nothing, Int, Unit](1 to nFibers) {
                counter =>
                  for {
                    m <- Message.create(counter)
                    _ <- MessageChannel.send(channel)(m)
                  } yield ()
              }
              _ <- MessageChannel.endChannel(channel)
            } yield ()

        // latches are used to ensure receivers finish in deterministic order for this test
        // don't use them in your own code
        // latches for each fiber - to be released in reverse order
        val latches = List.fill(nFibers)(new CountDownLatch(1))
        // release the 'first' latch - receivers will each release the next
        latches(nFibers - 1).countDown()

        def receiver: MessageChannel.Receiver[Console, Int] =
          message => {
            for {
              body <- UIO(message.body)
              _    <- UIO(latches(body - 1).await())
              _    <- putStr("finished " + body.toString)
              _    <- ZIO.when(body > 1)(UIO(latches(body - 2).countDown()))
            } yield ()
          }

        val program: ChannelHandle[Clock with Console] =
          MessageChannel.pointToPointPar(nFibers)(sender)(receiver)

        new DefaultRuntime {}.unsafeRunSync(program.runDrain)

        assert(
          output.get.reverse
            .containsSlice(List("finished 3", "finished 2", "finished 1")))
      }
    }
    "use cases" - {
      val output = new AtomicReference[List[String]](List.empty[String])

      def putStr(s: String): ZIO[Console, Nothing, Unit] =
        for {
          _ <- putStrLn(s)
          _ <- UIO(output.updateAndGet(l => s :: l))
        } yield ()

      def sender(id: String): MessageChannel.Sender[Clock with Console, Int] =
        channel =>
          for {
            _ <- ZIO.foreach(1 to 3) { counter =>
              for {
                message <- Message.create(counter)
                _       <- putStr(s"sender $id sending: " + message.body.toString)
                _       <- MessageChannel.send(channel)(message)
              } yield ()
            }
            _ <- MessageChannel.endChannel(channel)
          } yield ()

      def receiver(id: String): MessageChannel.Receiver[Console, Int] =
        message =>
          for {
            _ <- putStr(s"receiver $id received: " + message.body.toString)
          } yield ()

      "start second channel when first closes" in {
        //given
        val channel1 =
          MessageChannel.pointToPoint(sender("first"))(receiver("first"))
        val channel2 =
          MessageChannel.pointToPoint(sender("second"))(receiver("second"))
        //when
        val program: ZIO[Clock with Console, Unit, Unit] =
          channel1.runDrain *> channel2.runDrain
        //then
        new DefaultRuntime {}.unsafeRunSync(program)
        val expectedFirst = List(
          "sender first sending: 1",
          "sender first sending: 2",
          "sender first sending: 3",
          "receiver first received: 1",
          "receiver first received: 2",
          "receiver first received: 3"
        )
        val expectedSecond = List(
          "sender second sending: 1",
          "sender second sending: 2",
          "sender second sending: 3",
          "receiver second received: 1",
          "receiver second received: 2",
          "receiver second received: 3"
        )
        val (first, second) = output.get.reverse.splitAt(6)
        first should contain allElementsOf (expectedFirst)
        second should contain allElementsOf (expectedSecond)
      }
      "start third channel when two others both close" in {
        //given
        val channel1 =
          MessageChannel.pointToPoint(sender("first"))(receiver("first"))
        val channel2 =
          MessageChannel.pointToPoint(sender("second"))(receiver("second"))
        val channel3 =
          MessageChannel.pointToPoint(sender("third"))(receiver("third"))
        //when
        val program: ZIO[Clock with Console, Unit, Unit] =
          (channel1.runDrain <&> channel2.runDrain) *> channel3.runDrain
        //then
        new DefaultRuntime {}.unsafeRunSync(program)
        val expectedFirst = List(
          "sender first sending: 1",
          "sender first sending: 2",
          "sender first sending: 3",
          "receiver first received: 1",
          "receiver first received: 2",
          "receiver first received: 3",
          "sender second sending: 1",
          "sender second sending: 2",
          "sender second sending: 3",
          "receiver second received: 1",
          "receiver second received: 2",
          "receiver second received: 3"
        )
        val expectedSecond = List(
          "sender third sending: 1",
          "sender third sending: 2",
          "sender third sending: 3",
          "receiver third received: 1",
          "receiver third received: 2",
          "receiver third received: 3"
        )
        val (first, second) = output.get.reverse.splitAt(12)
        first should contain allElementsOf (expectedFirst)
        second should contain allElementsOf (expectedSecond)
      }
    }
  }
}
