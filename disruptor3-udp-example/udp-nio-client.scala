#!/usr/bin/scala
!#
import java.io._
import java.net._
import java.nio._
import java.nio.channels._
import java.util.concurrent.CountDownLatch

import scala.language.implicitConversions
implicit def funToRunnable(fun: () => Unit) = new Runnable() { def run() = fun() }

println("using client threads: " + args(0).toInt)
val CLIENT_THREADS = args(0).toInt
val latch = new CountDownLatch(CLIENT_THREADS)
for(i <- 1 to CLIENT_THREADS)
new Thread(() => {

  println("starting query thread " + i)

  val clientChannel = DatagramChannel.open
  clientChannel.configureBlocking(true)
  
  val endpoint = new InetSocketAddress("localhost", 9999)
  // clientChannel.connect(endpoint)
  val sentence = "a b c d e f g"
  val MAX = 1000000
  val times = new Array[Long](MAX)
  val sendData = sentence.getBytes

  val buffer = ByteBuffer.allocateDirect(2*1024)

  for(i <- -15000 until MAX) {
    buffer.clear
    buffer.put(sendData)
    buffer.flip
    val start = System.nanoTime
    clientChannel.send(buffer, endpoint)
    buffer.clear
    clientChannel.receive(buffer)
    if (i >= 0)
      times(i) = (System.nanoTime() - start)

    // println("RESPONSE WAS: " + new String(buffer.array))
  }

  println("ending query thread " + i)

  val sorted = times.sorted

  printf("UDP latency 1/50/99 percentile %.1f/%.1f/%.1f us%n",
    sorted(MAX / 100) / 1000d, sorted(MAX/2) / 1000d, sorted(MAX - MAX/100 - 1) / 1000d)

  latch.countDown

}).start

latch.await

// threads will terminate on their own
// server thread will end itself after socket opt timeout

