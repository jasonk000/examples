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
		    Writer writer = null;;
                    try {
                        writer = new OutputStreamWriter(new FileOutputStream("output-log.txt"), "utf-8");
                        while(true) {
                            byte[] receiveData = new byte[1024];
                            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                            server.receive(receivePacket);
			    final String receivedString = new String(receivePacket.getData());
			    // writer.write(receivedString);
			    // writer.write("\n");
                            byte[] sendData = receivedString.toUpperCase().getBytes();
                            server.send(new DatagramPacket(sendData,
                                                           sendData.length,
                                                           receivePacket.getAddress(),
                                                           receivePacket.getPort()));
                        }
		    } catch (SocketException e) {
			if (!e.toString().equals("java.net.SocketException: Socket closed")) {
			    System.out.println(e);
			    e.printStackTrace();
			}
                    } catch (Exception e) {
                        System.out.println(e);
                        e.printStackTrace();
                    } finally {
			try {
			    if (writer != null) writer.close();
			} catch(IOException e) {
			    System.out.println(e);
			    e.printStackTrace();
			}
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
