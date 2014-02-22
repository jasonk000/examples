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
        System.console().readLine("Press enter to exit.");
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
                            byte[] sendData = new String(receivePacket.getData()).toUpperCase().getBytes();
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
