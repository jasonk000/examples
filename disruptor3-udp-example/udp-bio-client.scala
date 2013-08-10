#!/usr/bin/scala
!#
import java.io._
import java.net._

import scala.language.implicitConversions
implicit def funToRunnable(fun: () => Unit) = new Runnable() { def run() = fun() }

for(i <- 1 to 1)
new Thread(() => {

  println("starting query thread " + i)

  val clientSocket = new DatagramSocket
  val endpoint = InetAddress.getByName("localhost")
  val sentence = "a b c d e f g"
  val MAX = 100000
  val times = new Array[Long](MAX)
  val sendData = sentence.getBytes

  for(i <- -15000 until MAX) {
    val receiveData = new Array[Byte](1024)
    val sendPacket = new DatagramPacket(sendData, sendData.length, endpoint, 9999)
    val receivePacket = new DatagramPacket(receiveData, receiveData.length)
    val start = System.nanoTime
    clientSocket.send(sendPacket)
    clientSocket.receive(receivePacket)
    if (i >= 0)
      times(i) = (System.nanoTime() - start)

    val returned = new String(receivePacket.getData)
    // println("RESPONSE WAS: " + returned)
  }

  println("ending query thread " + i)

  val sorted = times.sorted

  printf("UDP latency 1/50/99 percentile %.1f/%.1f/%.1f us%n",
    sorted(MAX / 100) / 1000d, sorted(MAX/2) / 1000d, sorted(MAX - MAX/100 - 1) / 1000d)

}).start

// threads will terminate on their own
// server thread will end itself after socket opt timeout

