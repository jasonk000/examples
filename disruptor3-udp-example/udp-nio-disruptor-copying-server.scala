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
 * This is the value event we will use to pass data between threads
 */
class DatagramEvent(var buffer:Array[Byte], var address:SocketAddress, var length:Int)

/**
 * Creates instances of DatagramEvent for the ring buffer
 */
object DatagramFactory extends EventFactory[DatagramEvent] {
  def newInstance = new DatagramEvent(new Array[Byte](BYTE_ARRAY_SIZE), null, 0)
}

/**
 * Sends the datagrams to the endpoints
 * 
 * Don't bother with endOfBatch, because it's assumed each udp packet goes to a different address
 */
class DatagramSendHandler() extends EventHandler[DatagramEvent] {
  val channel = DatagramChannel.open
  val buffer = ByteBuffer.allocate(BYTE_ARRAY_SIZE)
  def onEvent(event:DatagramEvent, sequence:Long, endOfBatch:Boolean) {
    if (event.address != null) {
      buffer.put(event.buffer, 0, event.length)
      buffer.flip
      channel.send(buffer, event.address)
      // assume the buffer isn't needed again, so clear it
      buffer.clear
    }
  }
}

/**
 * Business logic goes here
 */
class BusinessLogicHandler(val output:Disruptor[DatagramEvent]) extends EventHandler[DatagramEvent] {
  // translator will be used to write events into the buffer
  val translator = new ByteToDatagramEventTranslator
  // get a hold of the ringbuffer, we can't publish direct to Disruptor as the DSL doesn't
  // provide a garbage-free two-arg publishEvent method
  val ringbuffer = output.getRingBuffer

  // process events
  def onEvent(event:DatagramEvent, sequence:Long, endOfBatch:Boolean) {
    if (event.address != null) {
      // do the upper case work
      val toSendBytes = new String(event.buffer, 0, event.length).toUpperCase.getBytes
      // then publish direct to buffer
      ringbuffer.publishEvent(translator, toSendBytes, event.address)
    }
  }
}

/**
 * Pushes an output event onto the target disruptor
 * <p>
 * This uses the EventTranslatorTwoArg which has a special publishEvent facility on the ringbuffer
 * to avoid generating any garbage
 */
class ByteToDatagramEventTranslator extends EventTranslatorTwoArg[DatagramEvent, Array[Byte], SocketAddress] {
   def translateTo(event:DatagramEvent, sequence:Long, bytes:Array[Byte], address:SocketAddress) {
    event.address = address
    event.length = bytes.length
    System.arraycopy(bytes, 0, event.buffer, 0, bytes.length)
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
val RING_SIZE:Int = 1*1024
val BYTE_ARRAY_SIZE:Int = 1*1024

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

  val buffer = ByteBuffer.allocate(BYTE_ARRAY_SIZE)
  val channel = DatagramChannel.open
  channel.socket.bind(new InetSocketAddress(9999))
  channel.configureBlocking(true)

  println("ready.")

  while(true) {
    buffer.clear
    val socket = channel.receive(buffer)
    buffer.flip

    // use an anonymous inner class
    // otherwise we need to do a double copy; once out of the BB to a byte[], then from a byte[] to a byte[]
    disruptorIn.publishEvent(new EventTranslator[DatagramEvent] {
      def translateTo(event:DatagramEvent,sequence:Long) {
        event.length = buffer.remaining
        buffer.get(event.buffer, 0, buffer.remaining)
        event.address = socket
      }
    })

  }

  // shut down - note we'll never really get here unless someone interrupts the thread
  disruptorIn.shutdown
  inExecutor.shutdown
  disruptorOut.shutdown
  outExecutor.shutdown
}


