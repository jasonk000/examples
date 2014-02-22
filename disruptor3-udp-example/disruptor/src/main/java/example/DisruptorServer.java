qpackage example;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class DisruptorServer {

    public static void main(String[] args) {
        DisruptorServer d = new DisruptorServer(9999);
        d.start();
        System.console().readLine("Press enter to exit.")
        d.stop();
    }

    private static final int RING_SIZE = 1*1024;
    private static final int BYTE_ARRAY_SIZE = 1*1024;

    final ExecutorService inExecutor;
    final ExecutorService outExecutor;

    final int port;
    Thread t;

    public DisruptorServer(int port) throws SocketException {
        this.port = port;
    }

    public void start() throws Exception {

        executor = Executors.newFixedThreadPool(3);

        // start the transmit path
        private ExecutorService disruptorOut = new Disruptor<DatagramEvent>(
            DatagramEvent.EVENT_FACTORY, RING_SIZE, executor, ProducerType.SINGL                                                                                                                                                             E, new SleepingWaitStrategy());
        disruptorOut.handleEventsWith(new DatagramSendHandler());
        disruptorOut.start();

        // now start business logic step
        private ExecutorService disruptorIn = new Disruptor<DatagramEvent>(
            DatagramEvent.EVENT_FACTORY, RING_SIZE, executor, ProducerType.SINGL                                                                                                                                                             E, new SleepingWaitStrategy());
        disruptorIn.handleEventsWith(new PrintToConsoleHandler(), new BusinessLo                                                                                                                                                             gicHandler(disruptorOut));
        disruptorIn.start();

        // and now the receive path [single thread]
        final DatagramChannel channel = DatagramChannel.open();
        channel.socket.bind(new InetSocketAddress(port));
        channel.configureBlocking(true);
        t = new Thread(new ReceiveThread(channel));
        t.start();

        System.out.println("listening.");
    }

    public void stop() throws Exception {

        // early exit
        if (t == null) return;
        channel.close();
        t.interrupt();
        t.join();

        inExecutor.shutdown();
        outExecutor.shutdown();
        t = null;
    }

    /**
     * Implements the receive loop; this thread will never end until interrupted
     */
    private class ReceiveThread implements Runnable {
        private final DatagramChannel channel;
        public ReceiveThread(DatagramChannel channel) {
            this.channel = channel;
        }
        public void run() {
            final ByteBuffer buffer = ByteBuffer.allocate(BYTE_ARRAY_SIZE);
            // use this to map from the buffer across to the event
            EventTranslator<DatagramEvent> bufferTranslator = new EventTranslato                                                                                                                                                             r<DatagramEvent>() {
                public void translateTo(DatagramEvent event, long sequence) {
                    event.length = buffer.remaining();
                    buffer.get(event.buffer, 0, event.length);
                    event.address = socket;
                }
            };
            while(true) {
                // block to receive and wait for next round
                buffer.clear();
                final SocketAddress socket = channel.receive(buffer);
                disruptorIn.publishEvent(bufferTranslator);
                buffer.flip();
            }
        }
    }

    /**
     * Datagram value event
     * This is the value event we will use to pass data between threads
     */
    private class DatagramEvent  {
        public byte[] buffer;
        public SocketAddress address;
        public int length;

        public static final EventFactory<DatagramEvent> EVENT_FACTORY = new Even                                                                                                                                                             tFactory<DatagramEvent>()
        {
            public static DatagramEvent newInstance() {
                DatagramEvent e = new DatagramEvent;
                e.buffer = new byte[BYTE_ARRAY_SIZE];
                e.address = null;
                e.length = 0;
            }
        }

    }


    /**
     * Sends the datagrams to the endpoints
     * Don't bother with endOfBatch, because it's assumed each udp packet goes t                                                                                                                                                             o a different address
     */
    private class DatagramSendHandler extends EventHandler<DatagramEvent> {
        final DatagramChannel channel = DatagramChannel.open();
        final buffer = ByteBuffer.allocate(BYTE_ARRAY_SIZE);

        private void onEvent(DatagramEvent event, long sequence, boolean endOfBa                                                                                                                                                             tch) {
            buffer.clear();
            buffer.put(event.buffer, 0, event.length);
            buffer.flip();
            channel.send(buffer, event.address);
        }
    }

    /**
     * Business logic goes here
     */
    private class BusinessLogicHandler extends EventHandler<DatagramEvent> {
        final Disruptor<DatagramEvent> ringbuffer;
        final EventTranslatorTwoArg<DatagramEvent, byte[], SocketAddress> transl                                                                                                                                                             ator;

        public BusinessLogicHandler(Disruptor<DatagramEvent> output) {
            // translator will be used to write events into the buffer
            this.translator = new ByteToDatagramEventTranslator();
            // get a hold of the ringbuffer, we can't publish direct to Disrupto                                                                                                                                                             r as the DSL doesn't
            // provide a garbage-free two-arg publishEvent method
            this.ringbuffer = output.getRingBuffer();
        }

        /// process events
        public void onEvent(DatagramEvent event, long sequence, boolean endOfBat                                                                                                                                                             ch) {
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
    private class PrintToConsoleHandler extends EventHandler<DatagramEvent> {

        private static final int FLUSH_AFTER_SIZE = 1024;
        private static final byte[] NEWLINE = System.getProperty("line.separator                                                                                                                                                             ").getBytes();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        public void onEvent(DatagramEvent event, long sequence, boolean endOfBat                                                                                                                                                             ch) {
            out.write(event.buffer, 0, length);
            out.write(NEWLINE, 0, NEWLINE.length);
            if (endOfBatch || (out.size() > FLUSH_AFTER_SIZE)) {
                System.out.print(out.toString());
                out.reset();
            }

        }
    }

    /**
     * Pushes an output event onto the target disruptor
     * <p>
     * This uses the EventTranslatorTwoArg which has a special publishEvent faci                                                                                                                                                             lity on the ringbuffer
     * to avoid generating any garbage
     */
    private class ByteToDatagramEventTranslator extends EventTranslatorTwoArg<Da                                                                                                                                                             tagramEvent, byte[], SocketAddress> {
        public void translateTo(DatagramEvent event, long sequence, byte[] bytes                                                                                                                                                             , SocketAddress address) {
            event.address = address;
            event.length = bytes.length;
            System.arraycopy(bytes, 0, event.buffer, 0, bytes.length);
        }
    }

}

