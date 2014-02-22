package example;
import java.io.*;
import java.net.*;

import java.util.concurrent.*;

public class ThreadPoolServer {

    private Thread t;

    private final DatagramSocket server;
    private final ExecutorService executor;

    public static void main(String[] args) throws Exception {
	ThreadPoolServer s = new ThreadPoolServer(9999);
	s.start();
	System.console().readLine("Press enter to exit.");
	s.stop();
    }


    public ThreadPoolServer(int port) throws SocketException {
        this.server = new DatagramSocket(port);
	this.executor = Executors.newCachedThreadPool();
    }

    public void start() throws Exception {
	t = new Thread(new Runnable() {
		public void run() {
		    try {
			while(true) {
			    byte[] receiveData = new byte[1024];
			    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			    server.receive(receivePacket);
			    executor.submit(new PacketResponder(receivePacket));
			}
		    } catch (SocketException e) {
			if (!e.toString().equals("java.net.SocketException: Socket closed")) {
			    System.out.println(e);
			    e.printStackTrace();
			}
		    } catch (Exception e) {
			System.out.println(e);
			e.printStackTrace();
		    }
		}});
	t.start();	       	    
    }

    public void stop() throws Exception {
	if (t == null)
	    return;
	t.interrupt();
	server.close();
	t.join();
	executor.shutdown();
    }

    private class PacketResponder implements Runnable {
	private final DatagramPacket packet;
	public PacketResponder(DatagramPacket packet) {
	    this.packet = packet;
	}

	public void run() {
	    try {
		final DatagramSocket socket = new DatagramSocket();
		final byte[] sendData = new String(packet.getData()).toUpperCase().getBytes();
		socket.send(new DatagramPacket(sendData,
					       sendData.length,
					       packet.getAddress(),
					       packet.getPort()));
	    } catch (Exception e) {
		throw new RuntimeException(e);
	    }
	}
    }
}
		  
