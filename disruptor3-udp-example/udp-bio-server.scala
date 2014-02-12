#!/usr/local/bin/scala
!#
import java.io._
import java.net._
import java.util.concurrent.{CountDownLatch,ArrayBlockingQueue}

import scala.language.implicitConversions
implicit def funToRunnable(fun: () => Unit) = new Runnable() { def run() = fun() }

println("using threads: " + args(0).toInt)

val requestQ = new ArrayBlockingQueue[DatagramPacket](50)

for(i <- 1 to args(0).toInt)
new Thread(() => {
  var socket = new DatagramSocket
  while(true) {
    val receivePacket = requestQ.take
    val sentence = new String(receivePacket.getData())
    // println("RECEIVED: " + sentence)
    val endpoint = receivePacket.getAddress
    val port = receivePacket.getPort
    val sendData = sentence.toUpperCase.getBytes
    val sendPacket = new DatagramPacket(sendData, sendData.length, endpoint, port)
    socket.send(sendPacket)
    // println("SENT: " + new String(sendData))
  }
}).start

val serverSocket = new DatagramSocket(9999)
while (true) {
  val receiveData = new Array[Byte](1024)
  val receivePacket = new DatagramPacket(receiveData, receiveData.length)
  serverSocket.receive(receivePacket)
  requestQ.put(receivePacket)
}

serverSocket.close
