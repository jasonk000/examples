package example;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.*;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class DisruptorServer {

    public static void main(String[] args) throws Exception {
        DisruptorServer d = new DisruptorServer(9999);
        d.start();
        System.console().readLine("DisruptorServer running on port 9999. Press enter to exit.");
	System.exit(0);
        d.stop();
    }

    private static final int RING_SIZE = 1*1024;
    private static final int BYTE_ARRAY_SIZE = 1*1024;

    ExecutorService executor;
    DatagramChannel channel;

    final int port;
    Thread t;

    public DisruptorServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {

        executor = Executors.newFixedThreadPool(3);

        // start the transmit path
        Disruptor disruptorOut = new Disruptor<DatagramEvent>(
            DatagramEvent.EVENT_FACTORY, RING_SIZE, executor, ProducerType.SINGLE, new BlockingWaitStrategy());
        disruptorOut.handleEventsWith(new DatagramSendHandler());
        disruptorOut.start();

        // now start business logic step
        Disruptor disruptorIn = new Disruptor<DatagramEvent>(
            DatagramEvent.EVENT_FACTORY, RING_SIZE, executor, ProducerType.SINGLE, new BlockingWaitStrategy());
        // disruptorIn.handleEventsWith(new PrintToConsoleHandler(), new BusinessLogicHandler(disruptorOut));
	disruptorIn.handleEventsWith(new BusinessLogicHandler(disruptorOut));
        disruptorIn.start();

        // and now the receive path [single thread]
        channel = DatagramChannel.open();
        channel.socket().bind(new InetSocketAddress(port));
        channel.configureBlocking(true);
      	// channel.configureBlocking(false);
        t = new Thread(new ReceiveThread(channel, disruptorIn));
        t.start();

        System.out.println("listening.");
    }

    public void stop() throws Exception {

        // early exit
        if (t == null) return;
        channel.close();
        t.interrupt();
        t.join();

        executor.shutdown();
        t = null;
	channel = null;
    }

    /**
     * Implements the receive loop; this thread will never end until interrupted
     */
    private class ReceiveThread implements Runnable {
        private final DatagramChannel channel;
        private final Disruptor disruptor;
        public ReceiveThread(DatagramChannel channel, Disruptor disruptor) {
            this.channel = channel;
            this.disruptor = disruptor;
        }
        public void run() {
            final ByteBuffer buffer = ByteBuffer.allocate(BYTE_ARRAY_SIZE);
            try {
		while(true) {
		    // block to receive and wait for next round
		    buffer.clear();
		    final SocketAddress socket = channel.receive(buffer);
                    // if (socket == null) continue;
		    buffer.flip();
		    disruptor.publishEvent(new EventTranslator<DatagramEvent>() {
			    public void translateTo(DatagramEvent event, long sequence) {
				event.length = buffer.remaining();
				buffer.get(event.buffer, 0, event.length);
				event.address = socket;
			    }
			});
		}
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
        }
    }

    /**
     * Datagram value event
     * This is the value event we will use to pass data between threads
     */
    private static class DatagramEvent  {
        public byte[] buffer;
        public SocketAddress address;
        public int length;

        public static final EventFactory<DatagramEvent> EVENT_FACTORY = new EventFactory<DatagramEvent>() {
            public DatagramEvent newInstance() {
                DatagramEvent e = new DatagramEvent();
                e.buffer = new byte[BYTE_ARRAY_SIZE];
                e.address = null;
                e.length = 0;
		return e;
            }
        };

    }


    /**
     * Sends the datagrams to the endpoints
     * Don't bother with endOfBatch, because it's assumed each udp packet goes to a different address
     */
    private class DatagramSendHandler implements EventHandler<DatagramEvent> {
	final DatagramChannel channel;
	final ByteBuffer buffer;
	public DatagramSendHandler() throws IOException, SocketException {
	    channel = DatagramChannel.open();
            buffer = ByteBuffer.allocate(BYTE_ARRAY_SIZE);
	}

        public void onEvent(DatagramEvent event, long sequence, boolean endOfBatch) throws IOException {
            buffer.clear();
            buffer.put(event.buffer, 0, event.length);
            buffer.flip();
            channel.send(buffer, event.address);
        }
    }

    /**
     * Business logic goes here
     */
    private class BusinessLogicHandler implements EventHandler<DatagramEvent> {
        final RingBuffer<DatagramEvent> ringbuffer;
        final EventTranslatorTwoArg<DatagramEvent, byte[], SocketAddress> translator;

        public BusinessLogicHandler(Disruptor<DatagramEvent> output) {
            // translator will be used to write events into the buffer
            this.translator = new ByteToDatagramEventTranslator();
            // get a hold of the ringbuffer, we can't publish direct to Disruptor as the DSL doesn't
            // provide a garbage-free two-arg publishEvent method
            this.ringbuffer = output.getRingBuffer();
        }

        /// process events
        public void onEvent(DatagramEvent event, long sequence, boolean endOfBatch) {
            // then publish direct to buffer
            final byte[] toSendBytes = toUpperCase(event.buffer, event.length);
            ringbuffer.publishEvent(translator, toSendBytes, event.address);
        }

        private byte[] toUpperCase(byte[] input, int length) {
            return new String(input).toUpperCase().getBytes();
        }
    }

    /**
     * Print to console
     * Just to show the benefit of disruptor and mimic IO cost
     */
    private class PrintToConsoleHandler implements EventHandler<DatagramEvent> {

        private final int FLUSH_AFTER_SIZE = 1024;
        private final byte[] NEWLINE = System.getProperty("line.separator").getBytes();

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final Writer writer;

	public PrintToConsoleHandler() throws IOException {
	    out.reset();
	    writer = new OutputStreamWriter(new FileOutputStream("output-log.txt"), "utf-8");

	}

        public void onEvent(DatagramEvent event, long sequence, boolean endOfBatch) {
            out.write(event.buffer, 0, event.length);
            out.write(NEWLINE, 0, NEWLINE.length);
            if (endOfBatch || (out.size() > FLUSH_AFTER_SIZE)) {
		try {
		    writer.write(out.toString());
		} catch (IOException e) {
		    System.out.println("failed to write: ");
		    System.out.println(e);
		    e.printStackTrace();
		    throw new RuntimeException(e);
		}
                out.reset();
            }

        }
    }

    /**
     * Pushes an output event onto the target disruptor
     * <p>
     * This uses the EventTranslatorTwoArg which has a special publishEvent facility on the ringbuffer
     * to avoid generating any garbage
     */
    private class ByteToDatagramEventTranslator implements EventTranslatorTwoArg<DatagramEvent, byte[], SocketAddress> {
        public void translateTo(DatagramEvent event, long sequence, byte[] bytes, SocketAddress address) {
            event.address = address;
            event.length = bytes.length;
            System.arraycopy(bytes, 0, event.buffer, 0, bytes.length);
        }
    }

}

