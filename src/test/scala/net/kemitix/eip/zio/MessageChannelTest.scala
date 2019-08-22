package net.kemitix.eip.zio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import org.scalatest.FreeSpec
import zio.console.Console
import zio.{DefaultRuntime, UIO}

class MessageChannelTest extends FreeSpec {

  "Point-to-Point Message Channel" - {
    "receive messages asynchronously" in {
      val output = new AtomicReference[List[String]](List.empty[String])
      val latch  = new AtomicReference[CountDownLatch](new CountDownLatch(1))

      def putStr(s: String): Unit = {
        println(s)
        output.updateAndGet(l => s :: l)
      }

      def sender: MessageChannel.Sender[Int] =
        cb =>
          UIO {
            (1 to 3).foreach { message =>
              putStr("put " + message.toString)
              // use of latch is only to avoid tests being flaky
              // don't use them in your own code
              latch.set(new CountDownLatch(1))
              MessageChannel.send(cb)(message)
              // ensure receiver has completed:
              latch.get.await()
            }
            MessageChannel.endChannel(cb)
        }

      def receiver: MessageChannel.Receiver[Int] =
        message =>
          UIO {
            putStr("got " + message.toString)
            latch.get.countDown()
        }

      val program = MessageChannel.pointToPoint(sender)(receiver)
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

        def putStr(s: String): Unit = {
          println(s)
          output.updateAndGet(l => s :: l)
        }

        val nFibers = 3
        def sender: MessageChannel.Sender[Int] =
          cb =>
            UIO {
              (1 to nFibers).foreach(MessageChannel.send(cb))
              MessageChannel.endChannel(cb)
          }

        // latches are used to ensure receivers finish in deterministic order for this test
        // don't use them in your own code
        // latches for each fiber - to be released in reverse order
        val latches = List.fill(nFibers)(new CountDownLatch(1))
        // release the 'first' latch - receivers will each release the next
        latches(nFibers - 1).countDown()

        def receiver: MessageChannel.Receiver[Int] =
          message =>
            UIO {
              latches(message - 1).await()
              putStr("finished " + message.toString)
              if (message > 0) latches(message - 2).countDown()
          }

        val program = MessageChannel.pointToPointPar(nFibers)(sender)(receiver)
        new DefaultRuntime {}.unsafeRunSync(program.runDrain)
        assert(
          output.get.reverse
            .containsSlice(List("finished 3", "finished 2", "finished 1")))
      }
    }
    "with Environments" - {
      "receive messages asynchronously" in {
        val output = new AtomicReference[List[String]](List.empty[String])
        val latch  = new AtomicReference[CountDownLatch](new CountDownLatch(1))

        def putStr(s: String): Unit = {
          println(s)
          output.updateAndGet(l => s :: l)
        }

        def sender: MessageChannel.SenderR[Console, Int] =
          cb =>
            UIO {
              (1 to 3).foreach { message =>
                putStr("put " + message.toString)
                // use of latch is only to avoid tests being flaky
                // don't use them in your own code
                latch.set(new CountDownLatch(1))
                MessageChannel.sendR(cb)(message)
                // ensure receiver has completed:
                latch.get.await()
              }
              MessageChannel.endChannel(cb)
          }

        def receiver: MessageChannel.ReceiverR[Console, Int] =
          message =>
            UIO {
              putStr("got " + message.toString)
              latch.get.countDown()
          }

        val program =
          MessageChannel.pointToPointR[Console, Console, Int](sender)(receiver)
        new DefaultRuntime {}
          .unsafeRunSync(program.runDrain.provide(Console.Live))
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

          def putStr(s: String): Unit = {
            println(s)
            output.updateAndGet(l => s :: l)
          }

          val nFibers = 3
          def sender: MessageChannel.SenderR[Console, Int] =
            cb =>
              UIO {
                (1 to nFibers).foreach(MessageChannel.sendR(cb))
                MessageChannel.endChannel(cb)
            }

          // latches are used to ensure receivers finish in deterministic order for this test
          // don't use them in your own code
          // latches for each fiber - to be released in reverse order
          val latches = List.fill(nFibers)(new CountDownLatch(1))
          // release the 'first' latch - receivers will each release the next
          latches(nFibers - 1).countDown()

          def receiver: MessageChannel.ReceiverR[Console, Int] =
            message =>
              UIO {
                latches(message - 1).await()
                putStr("finished " + message.toString)
                if (message > 0) latches(message - 2).countDown()
            }

          val program =
            MessageChannel.pointToPointRPar(nFibers)(sender)(receiver)
          new DefaultRuntime {}
            .unsafeRunSync(program.runDrain.provide(Console.Live))
          assert(
            output.get.reverse
              .containsSlice(List("finished 3", "finished 2", "finished 1")))
        }
      }
    }
  }
}
