package example;
import java.io.*;
import java.net.*;
import java.util.Arrays;

public class BioServer {

    Thread t;
    final DatagramSocket server;

    public static void main(String[] args) throws Exception {
        BioServer s = new BioServer(9999);
        s.start();
        System.console().readLine("BioServer running on port 9999. Press enter to exit.");
	System.exit(0);
	s.stop();
    }


    public BioServer(int port) throws SocketException {
	this.server = new DatagramSocket(port);
    }

    public void start() throws Exception {
        // set up server
        t = new Thread(new Runnable() {
                public void run() {
                    try {
                        while(true) {
                            //System.out.println("receiving:");
                            byte[] receiveData = new byte[1024];
                            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                            server.receive(receivePacket);
                            //System.out.println("received:");
			    final String receivedString = new String(receivePacket.getData());
			    System.out.println(receivedString);
                            byte[] sendData = receivedString.toUpperCase().getBytes();
                            //System.out.println("sending:");
                            server.send(new DatagramPacket(sendData,
                                                           sendData.length,
                                                           receivePacket.getAddress(),
                                                           receivePacket.getPort()));
                            //System.out.println("sent.");
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
    }

}
