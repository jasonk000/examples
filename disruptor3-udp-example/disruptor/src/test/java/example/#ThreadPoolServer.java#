package example;

import java.net.*;
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@Threads(5)
public class ThreadPoolServerTest {

    private static final int PORT = 9999;
    private static final String ENDPOINT_NAME = "localhost";
    private static final byte[] SENTENCE_BYTES = "a b c d e f g".getBytes();

    private ThreadPoolServer server;

    private DatagramSocket clientSocket;
    private InetAddress endpoint;
    private byte[] receiveBuffer;
    private DatagramPacket sendPacket;
    private DatagramPacket receivePacket;

    private static int instantiated = 0;

    @Setup
    public synchronized void start() throws Exception {
        if (instantiated <= 0) {
            System.out.println("instantiating:");
	    server = new ThreadPoolServer(PORT);
	    server.start();
        }
        instantiated = instantiated + 1;

        clientSocket = new DatagramSocket();
        endpoint = InetAddress.getByName(ENDPOINT_NAME);
        receiveBuffer = new byte[1024];
        sendPacket = new DatagramPacket(SENTENCE_BYTES, SENTENCE_BYTES.length, endpoint, PORT);
        receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

    }


    @TearDown
    public synchronized void stop() throws Exception {
        instantiated = instantiated - 1;
        if (instantiated <= 0) server.stop();
    }

    @GenerateMicroBenchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public String udpRoundtrip() throws Exception {
        clientSocket.send(sendPacket);
        clientSocket.receive(receivePacket);
        final String response = new String(receivePacket.getData());
        return response;
    }

}
