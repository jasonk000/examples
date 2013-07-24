#!/usr/bin/scala
!#
import java.io._
import java.net._
import com.lmax.disruptor._
import com.lmax.disruptor.dsl.{Disruptor,ProducerType}
import java.util.concurrent.{CountDownLatch,Executors}

/**
 * Datagram value event
 */
// class DatagramEvent(var address:InetAddress, val data:Array[Byte], var length:Int, var port:Int)
// class DatagramEvent(val packet:DatagramPacket)

/**
 * Sends the datagrams to the endpoints
 * 
 * Don't bother with endOfBatch, because it's assumed each udp packet goes to a different address
 */
class DatagramSendHandler() extends EventHandler[DatagramPacket] {
  val socket = new DatagramSocket
  // val packet = new DatagramPacket(new Array[Byte](BYTE_ARRAY_SIZE), BYTE_ARRAY_SIZE)
  // def onEvent(event:DatagramEvent, sequence:Long, endOfBatch:Boolean) {
    // packet.setAddress(event.address)
    // packet.setLength(event.length)
    // packet.setPort(event.port)
    // packet.setData(event.data, 0, event.length)
    // socket.send(packet)
  // }
  def onEvent(event:DatagramPacket, sequence:Long, endOfBatch:Boolean) {
    socket.send(event)
  }
}

/**
 * Creates instances of DatagramEvent for the ring buffer
 */
// object DatagramFactory extends EventFactory[DatagramEvent] {
//   def newInstance = new DatagramEvent(null, new Array[Byte](BYTE_ARRAY_SIZE), BYTE_ARRAY_SIZE, 0)
// }
object DatagramFactory extends EventFactory[DatagramPacket] {
  def newInstance = new DatagramPacket(new Array[Byte](BYTE_ARRAY_SIZE), BYTE_ARRAY_SIZE)
}

/**
 * Business logic goes here
 */
class BusinessLogicHandler(val output:Disruptor[DatagramPacket]) extends EventHandler[DatagramPacket] {
  def onEvent(event:DatagramPacket, sequence:Long, endOfBatch:Boolean) {
    val dataToSend = new String(event.getData, event.getOffset, event.getLength).toUpperCase.getBytes
    output.publishEvent(new EventTranslator[DatagramPacket] {
      def translateTo(outEvent:DatagramPacket, sequence:Long) {
        outEvent.setAddress(event.getAddress)
        outEvent.setPort(event.getPort)
//        outEvent.setSocketAddress(event.getSocketAddress)
        outEvent.setData(dataToSend, 0, dataToSend.length)
      }
    })
  }
/*  def onEvent(event:DatagramEvent, sequence:Long, endOfBatch:Boolean) {
    val dataToSend = new String(event.data, 0, event.length).toUpperCase.getBytes

    // then publish
    output.publishEvent(new EventTranslator[DatagramPacket] {
      def translateTo(outEvent:DatagramEvent, sequence:Long) {
        System.arraycopy(dataToSend, 0, outEvent.data, 0, dataToSend.length)
        outEvent.address = event.address
        outEvent.length = dataToSend.length
        outEvent.port = event.port
        // event.setSocketAddress(event.datagram.getSocketAddress)
      }
    })
  }
*/
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
// val BYTE_ARRAY_SIZE:Int = 2*1024
val BYTE_ARRAY_SIZE:Int = 64

// class DatagramEvent(val address:InetAddress, val data:Array[Byte], val length:Int, val port:Int)

{ 
  // must have at least as many server threads as handlers, since we have a blocking translator
  val inExecutor = Executors.newFixedThreadPool(1)
  val outExecutor = Executors.newFixedThreadPool(1)
  val disruptorIn = new Disruptor[DatagramPacket](
    DatagramFactory, RING_SIZE, inExecutor, ProducerType.SINGLE, new BusySpinWaitStrategy)
  val disruptorOut = new Disruptor[DatagramPacket](
    DatagramFactory, RING_SIZE, outExecutor, ProducerType.SINGLE, new BusySpinWaitStrategy)
  disruptorOut.handleEventsWith(new DatagramSendHandler)
  disruptorOut.start
  disruptorIn.handleEventsWith(new BusinessLogicHandler(disruptorOut))
  disruptorIn.start

  val serverSocket = new DatagramSocket(9999)

  val packet = new DatagramPacket(new Array[Byte](BYTE_ARRAY_SIZE), BYTE_ARRAY_SIZE)
  while(true) {
    disruptorIn.publishEvent(new EventTranslator[DatagramPacket] {
      def translateTo(event:DatagramPacket, sequence:Long) = {
        serverSocket.receive(event)
      }
    })
/*    serverSocket.receive(packet)
    // packet is filled with address, port, length, and data
    // note that it may receive from an offset (?) not sure when
    disruptorIn.publishEvent(new EventTranslator[DatagramEvent] {
      def translateTo(event:DatagramEvent, sequence:Long) {
        event.address = packet.getAddress
        event.port = packet.getPort
        event.length = packet.getLength
        System.arraycopy(packet.getData, packet.getOffset, event.data, 0, packet.getLength)
      }
    })
*/
  }

  // shut down - note we'll never really get here unless someone interrupts the thread
  disruptorIn.shutdown
  disruptorOut.shutdown
  inExecutor.shutdown
  outExecutor.shutdown
}
