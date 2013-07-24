#!/usr/bin/scala
!#
import java.io._
import java.net._
import java.nio._
import java.nio.charset.Charset
import java.nio.channels._
import com.lmax.disruptor._
import com.lmax.disruptor.dsl.{Disruptor,ProducerType}
import java.util.concurrent.{CountDownLatch,Executors}

/**
 * Datagram value event
 */
class DatagramEvent(var buffer:ByteBuffer, var address:SocketAddress)

/**
 * Sends the datagrams to the endpoints
 * 
 * Don't bother with endOfBatch, because it's assumed each udp packet goes to a different address
 */
class DatagramSendHandler() extends EventHandler[DatagramEvent] {
  val channel = DatagramChannel.open
  def onEvent(event:DatagramEvent, sequence:Long, endOfBatch:Boolean) {
    if (event.address != null) {
      // assume that buffer is not yet flipped, so make it ready to send
      event.buffer.flip
      channel.send(event.buffer, event.address)
      // assume the buffer isn't needed again, so clear it
      event.buffer.clear
    }
  }
}

/**
 * Creates instances of DatagramEvent for the ring buffer
 */
object DatagramFactory extends EventFactory[DatagramEvent] {
  def newInstance = new DatagramEvent(ByteBuffer.allocateDirect(BYTE_ARRAY_SIZE), null)
}

/**
 * Business logic goes here
 * in this case, we upper-case the string
 * we take some shortcuts in assuming the charset of the string
 */
class BusinessLogicHandler(val output:Disruptor[DatagramEvent]) extends EventHandler[DatagramEvent] {
  val bytes = new Array[Byte](BYTE_ARRAY_SIZE)

  val translator = new EventTranslatorTwoArg[DatagramEvent, Array[Byte], SocketAddress] {
    def translateTo(outEvent:DatagramEvent, sequence:Long, outputBuff:Array[Byte], address:SocketAddress) {
      outEvent.buffer.put(outputBuff)
      outEvent.address = address
    }
  }

  def onEvent(event:DatagramEvent, sequence:Long, endOfBatch:Boolean) {
    if (event.address != null) {
 
      // fetch from source ringbuffer to our local "bytes" buffer
      val length = event.buffer.remaining
      event.buffer.get(bytes, 0, length)

      // uppercase to create an output buffer string
      val outputBuff:Array[Byte] = (new String(bytes)).toUpperCase.getBytes()

      // push to target - naive general version; creates a new ET for each processed event
      /* output.publishEvent(new EventTranslator[DatagramEvent] {
        def translateTo(outEvent:DatagramEvent, sequence:Long) {
          outEvent.buffer.put(outputBuff)
          outEvent.address = event.address
        }
      }) */

      // push to target - taking advantage of the supported event translators
      output.publishEvent(translator, outputBuff, event.address)

    }
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
  val executor = Executors.newFixedThreadPool(2)
  val disruptorIn = new Disruptor[DatagramEvent](
    DatagramFactory, RING_SIZE, executor, ProducerType.SINGLE, new SleepingWaitStrategy)
  val disruptorOut = new Disruptor[DatagramEvent](
    DatagramFactory, RING_SIZE, executor, ProducerType.SINGLE, new SleepingWaitStrategy)
  disruptorOut.handleEventsWith(new DatagramSendHandler)
  disruptorOut.start
  disruptorIn.handleEventsWith(new BusinessLogicHandler(disruptorOut))
  disruptorIn.start

  val channel = DatagramChannel.open
  channel.socket.bind(new InetSocketAddress(9999))
  channel.configureBlocking(true)

  while(true) {
    // this blocking receive is only OK since we have a single publisher
    // we couldn't have multiple events doing this, because the publishEvent() is effectively
    // getting a lock on the ring buffer before it calls translateTo
    // BUT - this is an effective way to get access to the DirectBB for the receive call
    disruptorIn.publishEvent(new EventTranslator[DatagramEvent] {
      def translateTo(event:DatagramEvent, sequence:Long) {
        event.address = null
        event.buffer.clear
        event.address = channel.receive(event.buffer)
        event.buffer.flip
      }
    })
  }

  // shut down - note we'll never really get here unless someone interrupts the thread
  disruptorIn.shutdown
  executor.shutdown
}
