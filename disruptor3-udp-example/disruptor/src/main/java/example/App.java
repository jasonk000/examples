package example;

import java.io.*;
import java.net.*;
import java.util.Arrays;

public class App 
{
    static final String SENTENCE = "a b c d e f g";
    static final String SENTENCE_UPPER = "A B C D E F G";
    static final byte[] SENTENCE_BYTES = SENTENCE.getBytes();

    public static void main( String[] args ) throws Exception
    {

	System.out.println("---- BioServer ----");
        BioServer server = new BioServer(9999);
        server.start();
        runClient();
        server.stop();

    }

    private static void runClient() throws Exception {
        // run warmup
        System.out.println("warmup:");
        runClientOnce("localhost", 9999, 30*1000);
        Thread.sleep(2000);

        // run client
        System.out.println("running:");
        final long start = System.currentTimeMillis();
        runClientOnce("localhost", 9999, 100*1000);
        runClientOnce("localhost", 9999, 100*1000);
        runClientOnce("localhost", 9999, 100*1000);
        runClientOnce("localhost", 9999, 100*1000);
        runClientOnce("localhost", 9999, 100*1000);

        final long end = System.currentTimeMillis();
        System.out.println("ran for (millis): " + (end-start));

    }

    private static void runClientOnce(String endpointName, int port, int iterations) throws Exception {
	final DatagramSocket clientSocket = new DatagramSocket();
	final InetAddress endpoint = InetAddress.getByName(endpointName);
	final long[] times = new long[iterations];
	final byte[] receiveBuffer = new byte[1024];
	final DatagramPacket sendPacket = new DatagramPacket(SENTENCE_BYTES, SENTENCE_BYTES.length, 
							     endpoint, port);
	final DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
    
	for(int i = 0; i < iterations; i++) {
	    final long start = System.nanoTime();
	    clientSocket.send(sendPacket);
	    clientSocket.receive(receivePacket);
	    times[i] = System.nanoTime() - start;
	    final String returned = new String(receivePacket.getData());
            // System.out.println("got back: " + returned);
	}
	
	Arrays.sort(times);

	System.out.printf("UDP latency 1/50/99 percentile %.1f/%.1f/%.1f us%n",
			  times[(int) (iterations * 0.01)] / 1000d,
			  times[(int) (iterations * 0.50)] / 1000d,
			  times[(int) (iterations * 0.99)] / 1000d);

    }
}
