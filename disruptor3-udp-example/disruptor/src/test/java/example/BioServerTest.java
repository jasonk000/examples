package example;

import java.net.*;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class BioServerTest {

    private static final int PORT = 9999;
    private static final String ENDPOINT_NAME = "localhost";
    private static final byte[] SENTENCE_BYTES = "a b c d e f g".getBytes();

    private BioServer server;

    private DatagramSocket clientSocket;
    private InetAddress endpoint;
    private byte[] receiveBuffer;
    private DatagramPacket sendPacket;		   
    private DatagramPacket receivePacket;

    @Setup
    public void start() throws Exception {
	server = new BioServer(PORT);
	server.start();

	clientSocket = new DatagramSocket();
	endpoint = InetAddress.getByName(ENDPOINT_NAME);
	receiveBuffer = new byte[1024];
	sendPacket = new DatagramPacket(SENTENCE_BYTES, SENTENCE_BYTES.length, endpoint, PORT);
	receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

    }

    @TearDown
    public void stop() throws Exception {
	server.stop();
    }

    @GenerateMicroBenchmark
    public String udpRoundtrip() throws Exception {
        clientSocket.send(sendPacket);
	clientSocket.receive(receivePacket);
	final String response = new String(receivePacket.getData());
	return response;
    }


}
