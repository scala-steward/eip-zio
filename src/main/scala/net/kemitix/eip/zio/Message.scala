package net.kemitix.eip.zio

import zio.{UIO, ZIO}
import zio.clock._

/**
  * A Message containing a Body and a collection of Headers.
  *
  * The headers are a Map of String to List of String.
  */
case class Message[+Body](
    headers: Map[String, List[String]],
    body: Body
)
object Message {
  def withBody[Body](body: Body): ZIO[Any, Nothing, Message[Body]] =
    UIO(Message[Body](headers = Map.empty, body = body))

  def create[Body](body: Body): ZIO[Clock, Nothing, Message[Body]] =
    currentDateTime.map { nt =>
      Message[Body](headers = Map(Headers.Created -> List(nt.toString)),
                    body = body)
    }
}
