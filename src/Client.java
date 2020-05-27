import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

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
	private OutputStream outToServer;
	private DataOutputStream out;
	private InputStream inFromServer;
	private DataInputStream in;
	
	private static final String HELO =  "HELO";
	private static final String AUTH =  "AUTH comp335";
	private static final String QUIT = "QUIT";
	private static final String REDY = "REDY";
	private static final String NONE = "NONE";
	private static final String ERR = "ERR: No such waiting job exists";
	private static final String RESC = "RESC Avail";
	private static final String RESCALL = "RESC All";
	private static final String OK = "OK";
	private static final String ERR2 = "ERR: invalid command (OK)";
	
	ArrayList<ArrayList<String>> allInfo = new ArrayList<ArrayList<String>>();
	
	public static void main(String args[]) {
		Client client = new Client("127.0.0.1", 50000);
	}
	
	public Client(String address, int port) {
		try {
			
			System.out.println("Attempting connection with " + address + " at port " + port);
			socket = new Socket(address,port);
			System.out.println("Connected");
			
			MSG(socket, HELO);//first message and reply from server: OK
			
			MSG(socket, AUTH);//second msg and reply from server: OK
			
			String job = MSG(socket, REDY);//third msg and reply from server: JOB1
			
			obtainServerInfo(socket);//add entire server information to the server arraylist
			
			ArrayList<String> firstMSG = noAvailServers(job);
			
			String servernum = firstMSG.get(1);
			String found = firstMSG.get(0);
			String jobN = getNumb(job, 2);
			
			MSG(socket, "SCHD " + jobN + " " + found + " " +servernum);
			
			while(true) {
				job = MSG(socket,REDY);
				if(job.contains(NONE) || job.contains(ERR)) {
					break;
				}
				
				if(job.contains(NONE) || job.contains(ERR)) {
					break;
				}
				
				//finding correct resc command for specific job
				int spaces = 0;
				int index = 0;
				for(int temp = 0; temp < job.length(); temp++) {
					if(job.charAt(temp) == ' ') {
						spaces++;
					}
					if(spaces == 4) {
						index = temp;
						break;
					}
				}
				
				//sending RESC command
				String jobDetails = job.substring(index);
				MSG(socket, RESC + jobDetails);//sends back data
				
				String servers = MSG(socket, OK);//first server info
				String foundServer = null;
				//writing OK while receiving info on servers,
				//also checks if all info has been sent
				while(!servers.substring(0, 1).contains(".")) {
					if(foundServer == null) {
						foundServer = canTakeAvailJob(servers, job);
					}

					servers = MSG(socket,OK);//going through the servers available
					
				}
				jobN = getNumb(job, 2);
				 if(foundServer != null) {
					servernum = getNumb(foundServer,1);
					foundServer = getNumb(foundServer,0);
					MSG(socket,"SCHD " + jobN + " " + foundServer + " " +servernum);
				}
				 else {
					firstMSG = noAvailServers(job);
						
					servernum = firstMSG.get(1);
					foundServer = firstMSG.get(0);
					jobN = getNumb(job, 2);
						
					MSG(socket, "SCHD " + jobN + " " + foundServer + " " +servernum);
				 }
			}
			
			//LAST STAGE: QUIT
			MSG(socket, QUIT);
			
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
	
	/**
	 * when starting the program store information of all servers
	 * before allocating the first job
	 * @param socket: to send to the MSG class
	 * @throws IOException: if there is any issue with connection
	 */
	public void obtainServerInfo(Socket socket) throws IOException {
		allInfo = new ArrayList<ArrayList<String>>();
		
		MSG(socket, RESCALL);//gain server information, returns DATA
		
		String serverInfo = MSG(socket,OK);//send ok, receive info on first server
		
		while(!serverInfo.substring(0,1).contains(".")){
			ArrayList<String> singleInfo = new ArrayList<>();
			String[] temp = serverInfo.split(" ");
			for(String details: temp) {//get all info from the server
				singleInfo.add(details);
			}
			//add to info arraylist and get next server
			allInfo.add(singleInfo);
			serverInfo = MSG(socket,OK);
		}
	}
	
	public ArrayList<String> noAvailServers(String job) {
		for(ArrayList<String> servers: allInfo) {
			if(canTakeJob(servers, job)) {
				return servers;
			}
		}
		return null;
	}
	
	public boolean canTakeJob(ArrayList<String> server, String job) {
		
		String memory = null;
		String diskspace = null;
		
		String jobMem = null;
		String jobDisk = null;

		memory = server.get(5);//gets memory for server
		diskspace = server.get(6);//gets diskspace for server
		
		jobMem = getNumb(job,5);//gets memory for job
		jobDisk = getNumb(job,6);//gets diskspace for job
		
		/**
		 * if the memory and diskspace of the server is large enough
		 * to hold the job then set the server to that server
		 * if not, then leave it as null
		 */
		if(Double.parseDouble(memory) > Double.parseDouble(jobMem) && Double.parseDouble(diskspace) > Double.parseDouble(jobDisk)) {
			return true;
		}
		
		return false;
	}
	
	public String canTakeAvailJob(String server, String job) {
		String hold = null;
		
		String memory = null;
		String diskspace = null;
		
		String jobMem = null;
		String jobDisk = null;

		memory = getNumb(server,5);//gets memory for server
		diskspace = getNumb(server,6);//gets diskspace for server
		
		jobMem = getNumb(job,5);//gets memory for job
		jobDisk = getNumb(job,6);//gets diskspace for job
		
		/**
		 * if the memory and diskspace of the server is large enough
		 * to hold the job then set the server to that server
		 * if not, then leave it as null
		 */
		if(Double.parseDouble(memory) > Double.parseDouble(jobMem) && Double.parseDouble(diskspace) > Double.parseDouble(jobDisk)) {
			hold = server;
		}
		
		return hold;
	}
	
	private String MSG(Socket socket, String msg) throws IOException {
		outToServer = socket.getOutputStream();
		out = new DataOutputStream(outToServer);
		
		out.write(msg.getBytes());//write msg
		out.flush();
		System.out.println("Sent msg to server " + msg);
		
		
		inFromServer = socket.getInputStream();
		in = new DataInputStream(inFromServer);
		
		byte[] rMSG = new byte[1024];
		in.read(rMSG);

		String input = new String(rMSG);//read reply
		System.out.println("Received msg from server " + input);
		return input;
	}
	
	/**
	 * Finds the number after a certain space
	 * from both the job and the server information
	 * memory info is held after space 5
	 * diskspace info is held after space 6
	 */
	public static String getNumb(String address, int spaces) {
		int spc = 0;
		int subindex = 0;
		String numb = null;
		
		if(address.length() < 10) {//just in case a shorter message (such as DATA gets through)
			return null;
		}
		
		for(int temp = 0; temp < address.length(); temp++) {//gets the index of the required space
			if(address.charAt(temp) == ' ') {
				spc++;
			}
			if(spc == spaces) {
				subindex = temp;
				break;
			}
		}
		
		int finalIndex = subindex +1;
		if(spaces <= 5) {//if the space is not for the final number in the message
			while(address.charAt(finalIndex) != ' ') {
				finalIndex++;
			}
		} else {//if it is
			finalIndex = address.length();
		}
		
		if(spaces != 0) {//if the space is for the name of the server/job
			numb = address.substring(subindex+1,finalIndex);
		} else {//if it is not
			numb = address.substring(subindex,finalIndex);
		}
		
		return numb;
	}

}
