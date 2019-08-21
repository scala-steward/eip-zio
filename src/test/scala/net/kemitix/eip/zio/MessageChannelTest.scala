package net.kemitix.eip.zio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import org.scalatest.FreeSpec
import zio.{DefaultRuntime, UIO, ZIO}

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
              putStr(s"put $message")
              latch.set(new CountDownLatch(1))
              cb(ZIO.succeed(message))
              // ensure receiver has completed:
              latch.get.await()
            }
            MessageChannel.endChannel(cb)
        }

      def receiver: MessageChannel.Receiver[Int] =
        message =>
          UIO {
            putStr(s"got $message")
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
              (1 to nFibers).foreach { message =>
                cb(ZIO.succeed(message))
              }
              MessageChannel.endChannel(cb)
          }

        def receiver: MessageChannel.Receiver[Int] =
          message =>
            UIO {
              Thread.sleep((nFibers - message) * 10) // reverse the order of completion
              putStr(s"finished $message")
          }

        val program = MessageChannel.pointToPointPar(3)(sender)(receiver)
        new DefaultRuntime {}.unsafeRunSync(program.runDrain)
        assert(
          output.get.reverse
            .containsSlice(List("finished 3", "finished 2", "finished 1")))
      }
    }
  }
}
