import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * 
 * @author chrispurkiss
 * 
 * First fit is an algorithm to send
 * a job to the first available or capable server
 * with the capacity to run that job
 */

public class Client {
	private Socket socket;
	private DataOutputStream out;
	private BufferedReader in;
	
	private static final String HELO =  "HELO";
	private static final String AUTH =  "AUTH comp335";
	private static final String QUIT = "QUIT";
	private static final String REDY = "REDY";
	private static final String NONE = "NONE";
	private static final String ERR = "ERR: No such waiting job exists";
	private static final String RESC = "RESC Avail";
	private static final String OK = "OK";
	private static final String ERR2 = "ERR: invalid command (OK)";
	
	
	public static void main(String args[]) {
		Client client = new Client("127.0.0.1", 50000);
	}
	
	public Client(String address, int port) {
		try {
			System.out.println("Attempting connection with " + address + " at port " + port);
			socket = new Socket(address,port);
			System.out.println("Connected");
			
			MSG(socket, HELO);
			
		} catch (UnknownHostException u) {
			System.out.println(u);
		}
		catch (IOException e) {
			System.out.println(e);
		}
		try {
			in.close();
			out.close();
			socket.close();
		} catch (IOException e) {
			System.out.println(e);
		}
	}
	
	private String MSG(Socket socket, String msg) throws IOException {
		out.write(msg.getBytes());//write msg
		out.flush();

		String input = in.readLine();//read reply
		return input;
	}

}
