#!/usr/bin/scala
!#
import java.io._
import java.net._
import java.nio._
import java.nio.channels._
import java.util.concurrent.CountDownLatch
import java.util.concurrent.{ArrayBlockingQueue,SynchronousQueue}

import scala.language.implicitConversions
implicit def funToRunnable(fun: () => Unit) = new Runnable() { def run() = fun() }

class Request(val socket:SocketAddress, val buffer:ByteBuffer)

// val requestQ = new ArrayBlockingQueue[Request](50)
val requestQ = new SynchronousQueue[Request]
println("using threads: " + args(0).toInt)
for(i <- 1 to args(0).toInt)
new Thread(() => {
//  var channel = DatagramChannel.open
  while(true) {
    val request = requestQ.take
    request.buffer.flip
    val sentenceIn = new String(request.buffer.array)
    val sentenceOut = sentenceIn.toUpperCase
    val sendBuffer = ByteBuffer.wrap(sentenceOut.getBytes)
    channel.send(sendBuffer, request.socket)
  }
}).start

val channel = DatagramChannel.open
channel.socket.bind(new InetSocketAddress(9999))
val bb = ByteBuffer.allocate(1024 * 2) 
channel.configureBlocking(true)

while (true) {
  val bb = ByteBuffer.allocate(1024 * 2)
  val sender = channel.receive(bb)
  if (sender != null) {
    requestQ.put(new Request(sender, bb))
  }
}

