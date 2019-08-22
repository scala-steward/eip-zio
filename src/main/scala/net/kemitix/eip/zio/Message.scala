package net.kemitix.eip.zio

import zio.ZIO
import zio.clock._

/**
  * A Message containing a Body and a collection of Headers.
  *
  * The headers are a Map of String to List of String.
  */
case class Message[Body] private (
    headers: Map[String, List[String]],
    body: Body
)
object Message {
  def create[Body](body: Body): ZIO[Clock, Nothing, Message[Body]] =
    currentDateTime.map { nt =>
      Message[Body](headers = Map(Headers.Created -> List(nt.toString)),
                    body = body)
    }
}
