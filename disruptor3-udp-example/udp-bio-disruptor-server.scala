#!/usr/bin/scala
!#
import java.io._
import java.net._
import java.nio._
import java.nio.channels._
import com.lmax.disruptor._
import com.lmax.disruptor.dsl.{Disruptor,ProducerType}
import java.util.concurrent.{CountDownLatch,Executors}

/**
 * Datagram value event
 */
class DatagramEvent(val datagram:DatagramPacket)

/**
 * Sends the datagrams to the endpoints
 * 
 * Don't bother with endOfBatch, because it's assumed each udp packet goes to a different address
 */
class DatagramSendHandler() extends EventHandler[DatagramEvent] {
  var socket = new DatagramSocket
  def onEvent(event:DatagramEvent, sequence:Long, endOfBatch:Boolean) {
    socket.send(event.datagram)
  }
}

/**
 * Creates instances of DatagramEvent for the ring buffer
 */
object DatagramFactory extends EventFactory[DatagramEvent] {
  def newInstance = new DatagramEvent(new DatagramPacket(new Array[Byte](BYTE_ARRAY_SIZE), BYTE_ARRAY_SIZE))
}

/**
 * Business logic goes here
 */
class BusinessLogicHandler(val output:Disruptor[DatagramEvent]) extends EventHandler[DatagramEvent] {
  def onEvent(event:DatagramEvent, sequence:Long, endOfBatch:Boolean) {
    val endpoint = event.datagram.getAddress
    val port = event.datagram.getPort
    val dataToSend = new String(event.datagram.getData).toUpperCase.getBytes

    // then publish
    output.publishEvent(new EventTranslator[DatagramEvent] {
      def translateTo(outEvent:DatagramEvent, sequence:Long) {
        System.arraycopy(dataToSend, 0, outEvent.datagram.getData, 0, dataToSend.length)
        outEvent.datagram.setAddress(event.datagram.getAddress)
        outEvent.datagram.setLength(dataToSend.length)
        outEvent.datagram.setPort(event.datagram.getPort)
        outEvent.datagram.setSocketAddress(event.datagram.getSocketAddress)
      }
    })
  }
}

/**
 * Server thread - event loop
 * 
 * We took a shortcut in the EventTranslator, since it does a blocking read, then
 * all the server thread needs to do is request a new publish. The disruptor will request
 * the translator to fill next event, and translator can write direct to the buffer.
 * This would not work if we had multiple publishers, since the translator is effectively
 * blocking the sequence from progressing while it is blocked in translateTo()
 *
 * The "in" disruptor is responsible for reading from UDP, doing the upper case transform
 * (the business logic), and then copying the packet to the "out" disruptor"
 */
val RING_SIZE:Int = 16*1024
val BYTE_ARRAY_SIZE:Int = 2*1024

{ 
  // must have at least as many server threads as handlers, since we have a blocking translator
  val inExecutor = Executors.newFixedThreadPool(1)
  val outExecutor = Executors.newFixedThreadPool(1)
  val disruptorIn = new Disruptor[DatagramEvent](
    DatagramFactory, RING_SIZE, inExecutor, ProducerType.SINGLE, new SleepingWaitStrategy)
  val disruptorOut = new Disruptor[DatagramEvent](
    DatagramFactory, RING_SIZE, outExecutor, ProducerType.SINGLE, new SleepingWaitStrategy)
  disruptorOut.handleEventsWith(new DatagramSendHandler)
  disruptorOut.start
  disruptorIn.handleEventsWith(new BusinessLogicHandler(disruptorOut))
  disruptorIn.start

  val serverSocket = new DatagramSocket(9999)

  while(true) {
    disruptorIn.publishEvent(new EventTranslator[DatagramEvent] {
      def translateTo(event:DatagramEvent, sequence:Long) {
        serverSocket.receive(event.datagram)
      }
    })
  }

  // shut down - note we'll never really get here unless someone interrupts the thread
  disruptorIn.shutdown
  disruptorOut.shutdown
  inExecutor.shutdown
  outExecutor.shutdown
}
