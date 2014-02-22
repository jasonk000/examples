package example;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.*;

public class App 
{
    static final String SENTENCE = "a b c d e f g";
    static final String SENTENCE_UPPER = "A B C D E F G";
    static final byte[] SENTENCE_BYTES = SENTENCE.getBytes();
    static ExecutorService pool;

    public static void main( String[] args ) throws Exception
    {
        if (args.length != 2) {
            System.out.println("arguments: <hostname> <parallelthreads>");
            return;
        }
        System.out.println("running with thread count = " + Integer.parseInt(args[1]));
        pool = Executors.newFixedThreadPool(Integer.parseInt(args[1]) + 1);
        runClient(args[0], Integer.parseInt(args[1]));
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
    }

    private static void runClient(final String host, final int threads) throws Exception {

        for(int i = 0; i < threads; i++) {
            pool.submit(new Runnable() {
                public void run() {
                    try{ 
                    // run warmup
                    System.out.println(Thread.currentThread().getName() + ": warmup:");
                    runClientOnce(host, 9999, 20*1000);
                    Thread.sleep(2000);

                    // run client
                    System.out.println(Thread.currentThread().getName() + ": running:");
                    final long start = System.currentTimeMillis();
                    runClientOnce(host, 9999, 10*1000);
                    runClientOnce(host, 9999, 10*1000);
                    runClientOnce(host, 9999, 10*1000);
                    runClientOnce(host, 9999, 10*1000);
                    runClientOnce(host, 9999, 10*1000);

                    final long end = System.currentTimeMillis();
                    System.out.println(Thread.currentThread().getName() + ": ran for (millis): " + (end-start));
                    } catch (Exception e) { 
                        throw new RuntimeException(e);
                    }
                }
            });
        }

    }

    private static void runClientOnce(String endpointName, int port, int iterations) throws Exception {
	final DatagramSocket clientSocket = new DatagramSocket();
	final InetAddress endpoint = InetAddress.getByName(endpointName);
	final long[] times = new long[iterations];
	final byte[] receiveBuffer = new byte[1024];
	final DatagramPacket sendPacket = new DatagramPacket(SENTENCE_BYTES, SENTENCE_BYTES.length, endpoint, port);
	final DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
    
	for(int i = 0; i < iterations; i++) {
	    final long start = System.nanoTime();
	    clientSocket.send(sendPacket);
	    clientSocket.receive(receivePacket);
	    times[i] = System.nanoTime() - start;
	    final String returned = new String(receivePacket.getData());
	}
	
	Arrays.sort(times);

	System.out.printf(Thread.currentThread().getName() + ": UDP latency 1/50/99 percentile %.1f/%.1f/%.1f us%n",
			  times[(int) (iterations * 0.01)] / 1000d,
			  times[(int) (iterations * 0.50)] / 1000d,
			  times[(int) (iterations * 0.99)] / 1000d);

    }
}
