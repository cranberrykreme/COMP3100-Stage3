import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
	
	static int chosenAlgorithm = -1;
	static int accessAlgorithms = 0;
	
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
	ArrayList<ArrayList<String>> allInitialInfo = new ArrayList<ArrayList<String>>();
	ArrayList<String> sortOrder = new ArrayList<String>();
	
	public static void main(String args[]) {
		for(int i = 0; i < args.length; i++) {
			if(args[i].contains("-a")) {//user wants to choose algorithm
				accessAlgorithms = 1;
				if(args[i+1].contains("ff")) {//user wants to choose first fit
					chosenAlgorithm = 1;
				} else if(args[i+1].contains("bf")) {
					chosenAlgorithm = 2;
				} else if(args[i+1].contains("wf")) {
					chosenAlgorithm = 3;
				} else if(args[i+1].contains("lar")) {
					chosenAlgorithm = 0;
				} else if(args[i+1].contains("fs")) {
					chosenAlgorithm = 4;
				}
			}
			if(args[i].contains("help")) {
				System.out.println("IN ORDER TO USE THIS CLIENT ALGORITHM YOU FIRST HAVE TO");
				System.out.println("WRITE -a FOLLOWED BY YOU'RE CHOICE OF ALGORITHM");
				System.out.println("ff - first fit");
				System.out.println("bf - best fit");
				System.out.println("wf - worst fit");
				System.out.println("lar - largest server");
			}
		}
		if(accessAlgorithms == 1) {
			Client client = new Client("127.0.0.1", 50000);
		}

	}
	
	public Client(String address, int port) {
		try {
			
			System.out.println("Attempting connection with " + address + " at port " + port);
			socket = new Socket(address,port);
			System.out.println("Connected");
			
			if(chosenAlgorithm == 0) {
				System.out.println("largest server algorithm not supported by this Client");
				System.out.println("please write ff for first fit algorithm");
				return;
			} else if(chosenAlgorithm == 2) {
				System.out.println("best-fit server algorithm not supported by this Client");
				System.out.println("please write ff for first fit algorithm");
				return;
			} else if(chosenAlgorithm == 3) {
				System.out.println("worst-fit server algorithm not supported by this Client");
				System.out.println("please write ff for first fit algorithm");
				return;
			} else if(chosenAlgorithm == -1) {
				System.out.println("ERROR: PLEASE INPUT AN ALGORITHM TO RUN THE CLIENT	");
				System.out.println("please write -a <algorithm> to access a specific algorithm");
				return;
			} else if(chosenAlgorithm == 1) {
				firstFit(socket);
			} else if(chosenAlgorithm == 4) {
				fastServer(socket);
			}
			System.out.println(sortOrder);
			//LAST STAGE: QUIT
			MSG(socket, QUIT);
			
		} catch (UnknownHostException u) {
			System.out.println(u);
		}
		catch (IOException e) {
			System.out.println(e);
		}
		try {
			inFromServer.close();
			outToServer.close();
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
	
	/**
	 * first fit sorts the servers
	 * then sends them a job to the first server
	 * which can run it
	 * @param socket
	 * @throws IOException
	 */
	public void firstFit(Socket socket) throws IOException {
		MSG(socket, HELO);//first message and reply from server: OK
		
		MSG(socket, AUTH);//second msg and reply from server: OK
		
		String job = MSG(socket, REDY);//third msg and reply from server: JOB1
		
		obtainServerInfo(socket);//add entire server information to the server arraylist
		
		allInitialInfo = allInfo;//set the initial servers capacity
		allInitialInfo = sort(allInitialInfo, allInitialInfo.size(), true);
		setServerOrder();
		
		
		ArrayList<String> foundServer = getServers(job,allInfo);//finds the best server for the first job
		
		String servernum = foundServer.get(1);
		String found = foundServer.get(0);
		String jobN = getNumb(job, 2);
		
		MSG(socket, "SCHD " + jobN + " " + found + " " +servernum);//schedules the first job
		
		while(true) {
			job = MSG(socket,REDY);//ready message and reply from server; JOB#
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
			String jobInfo = job.substring(index);
			obtainServerInfo(socket);
			allInfo = sort(allInfo, allInfo.size(), false);
			boolean temp = false;
			foundServer = getServers(job,allInfo);
			//sending RESC command
			//String jobDetails = job.substring(index);
			//MSG(socket, RESC);//sends back data
			
			
			 if(foundServer != null) {
				servernum = foundServer.get(1);
				found = foundServer.get(0);
				jobN = getNumb(job,2);
				MSG(socket,"SCHD " + jobN + " " + found + " " +servernum);
			}
			 else {
				foundServer = getServers(job, allInitialInfo);
					
				servernum = foundServer.get(1);
				found = foundServer.get(0);
				jobN = getNumb(job, 2);
					
				MSG(socket, "SCHD " + jobN + " " + found + " " +servernum);
			 }
		}
		System.out.println("original info: " + allInitialInfo);
		System.out.println("non-original info: " + allInfo);
	}
	
	/**
	 * attempts to allocate the jobs in the fastest possible
	 * time
	 * @param socket
	 * @throws IOException
	 */
	public void fastServer(Socket socket) throws IOException{
		
		
		//obtainServerInfo(socket);//initialize the arrayList
		
		MSG(socket, HELO);//first message and reply from server: OK
		
		MSG(socket, AUTH);//second msg and reply from server: OK
		
		//parse system.xml
		//File file = new File("/home/comp335/ds-sim/configs/system.xml");
		File file = new File("/Users/chrispurkiss/Stage2Sub/ds-sim/system.xml");
				
		ArrayList<ArrayList<String>> str = parse(file);
		
		String server = null;
		
		String jobN = null;
		
		while(true) {
			String job = MSG(socket, REDY);//msg redy for jobn
			if(job.contains(NONE) || job.contains(ERR)) {
				break;
			}
			
			boolean foundServer = false;
			
			String type = getNumb(job, 0);
			Double coreCount = Double.parseDouble(getNumb(job, 4));
			Double memory = Double.parseDouble(getNumb(job, 5));
			Double disk = Double.parseDouble(getNumb(job, 6));
			
			int serverN = 0;
			
			jobN = getNumb(job, 2);
			
			for(ArrayList<String> list: str) {
				if(coreCount <= Double.parseDouble(list.get(1)) && memory <= Double.parseDouble(list.get(2)) && disk <= Double.parseDouble(list.get(3))) {
					server = list.get(0);
					foundServer = true;
					if(Integer.parseInt(list.get(5)) < (Integer.parseInt(list.get(4)))) {//is the last server used at the limit of the server type?
						serverN = Integer.parseInt(list.get(5));
						String num = Integer.toString(serverN+1);
						list.set(5,num);
					} else {
						String num = "1";
						serverN = 0;
						list.set(5, num);
					}
					
					break;
				} else {
					System.out.println("server " + list + " cannot run this job " + job);
				}
			}
			
			if(foundServer) {
				MSG(socket, "SCHD " + jobN + " " + server + " " + serverN);
			}
		}
		
		
		
	}
	
	/*
	 * get information out of file and return
	 * the largest server (the one with the most cores)
	 */
	private ArrayList<ArrayList<String>> parse(File file) {//todo: find the first server that can take the job
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(file);
			ArrayList<ArrayList<String>> str = new ArrayList<ArrayList<String>>();

			
			doc.getDocumentElement().normalize();
			
			NodeList nList = doc.getElementsByTagName("server");
			
			System.out.println("made it to 1");
			
			if(doc.hasChildNodes()) {
				for(int i = 0; i < nList.getLength(); i++) {
					Node node = nList.item(i);
					ArrayList<String> list = new ArrayList<String>();
					
					if(node.getNodeType() == Node.ELEMENT_NODE) {
						Element element = (Element) node;
						System.out.println("made it to 1.5");
						list.add(0, element.getAttribute("type"));
						System.out.println("made it to 1.7");
						list.add(1,element.getAttribute("coreCount"));
						list.add(2,element.getAttribute("memory"));
						list.add(3,element.getAttribute("disk"));
						list.add(4, element.getAttribute("limit"));//maximum number of servers of this type
						list.add(5, "0");
						
						str.add(list);
					}
				}
			}
			
			System.out.println("made it to 2");
			return str;
			
		} catch (Exception e) {
			System.out.println(e);
		}
		System.out.println("did not work!!!!");
		return null;
	}
	
	/**
	 * if there are no avialable servers, returns the first server
	 * which can take the job from the preformatted arraylist
	 * @param job
	 * @param first
	 * @return
	 */
	public ArrayList<String> getServers(String job, ArrayList<ArrayList<String>> list) {
		for(ArrayList<String> servers: list) {
			if(canTakeJob(servers, job) && serverAvail(servers)) {
				return servers;
			}
		}
		return null;
	}
	
	/**
	 * returns true if the server is available
	 * @param servers
	 * @param first
	 * @return
	 */
	public boolean serverAvail(ArrayList<String> servers) {
		String available = servers.get(2);
		int avail = Integer.parseInt(available);
		
		if(avail == 2 || avail == 3 || avail == 0) {
			return true;
		}
		return false;
	}
	
	/**
	 * returns true if the server can take the job (RESCALL)
	 * @param server
	 * @param job
	 * @return
	 */
	public boolean canTakeJob(ArrayList<String> server, String job) {
		
		String memory = null;
		String diskspace = null;
		String cores = null;
		
		String jobMem = null;
		String jobDisk = null;
		String jobCores = null;

		memory = server.get(5);//gets memory for server
		diskspace = server.get(6);//gets diskspace for server
		cores = server.get(4);//gets cores for server
		
		jobMem = getNumb(job,5);//gets memory for job
		jobDisk = getNumb(job,6);//gets diskspace for job
		jobCores = getNumb(job,4);//gets cores for job
		
		/**
		 * if the memory and diskspace of the server is large enough
		 * to hold the job then set the server to that server
		 * if not, then leave it as null
		 */
		if(Double.parseDouble(memory) >= Double.parseDouble(jobMem) && Double.parseDouble(diskspace) >= Double.parseDouble(jobDisk) && Double.parseDouble(cores) >= Double.parseDouble(jobCores)) {
			return true;
		}
		
		return false;
	}
	
	/**
	 * sends and receives messages from the server
	 * @param socket
	 * @param msg
	 * @return
	 * @throws IOException
	 */
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
	 * #CPU cores is held after space 4
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
	
	/**
	 * merge sort used to put the smaller servers first
	 * @param list
	 * @param len
	 * @return sorted arraylist
	 */
	public ArrayList<ArrayList<String>> sort(ArrayList<ArrayList<String>> list, int len, boolean original) {
		if(len< 2) {
			return list;
		}
		
		int mid = len/2;
		ArrayList<ArrayList<String>> left = new ArrayList<ArrayList<String>>();
		ArrayList<ArrayList<String>> right = new ArrayList<ArrayList<String>>();
		
		for(int i = 0; i < mid; i++) {
			left.add(list.get(i));
		}
		for(int i = mid; i < len; i++) {
			right.add(list.get(i));
		}
		
		sort(left, left.size(), original);
		sort(right, right.size(), original);
		
		if(original) {
			list = merge(list,left,right,mid,len-mid);
		} else {
			list = combine(list,left,right,mid,len-mid);
		}
		return list;
	}
	
	public ArrayList<ArrayList<String>> merge(ArrayList<ArrayList<String>> original, ArrayList<ArrayList<String>> left, ArrayList<ArrayList<String>> right, int leftLength, int rightLength){
		int i = 0, j = 0, k = 0;
		
		while(i < leftLength && j < rightLength) {
			double lCore = Double.parseDouble(left.get(i).get(4));
			double rCore = Double.parseDouble(right.get(i).get(4));
			
			double lNumb = Double.parseDouble(left.get(i).get(1));
			double rNumb = Double.parseDouble(right.get(i).get(1));

				if(lCore < rCore) {
					original.set(k++, left.get(i++));
				} else if (lCore == rCore && lNumb < rNumb) {
					original.set(k++, left.get(i++));
				}
				else {
					original.set(k++, right.get(j++));
				}

		}
		while(i<leftLength) {
			original.set(k++, left.get(i++));
		}
		while(j<rightLength) {
			original.set(k++, right.get(j++));
		}
		
		return original;
	}
	
	public ArrayList<ArrayList<String>> combine(ArrayList<ArrayList<String>> original, ArrayList<ArrayList<String>> left, ArrayList<ArrayList<String>> right, int leftLength, int rightLength){
		int i = 0, j = 0, k = 0;
		
		while(i < leftLength && j < rightLength) {
			double lNumb = Double.parseDouble(left.get(i).get(1));
			double rNumb = Double.parseDouble(right.get(i).get(1));
			
			String lName = left.get(i).get(0);
			String rName = right.get(i).get(0);
			
			int lIndex = -1;
			int rIndex = -1;
			
			for(int index = 0;index < sortOrder.size(); index++) {
				if(sortOrder.get(index).equals(lName)) {
					lIndex = index;
					break;
				}
			}
			
			for(int index = 0; index < sortOrder.size(); index++) {
				if(sortOrder.get(index).equals(rName)) {
					rIndex = index;
					break;
				}
			}

				if(lIndex < rIndex) {
					original.set(k++, left.get(i++));
				}else if(lIndex == rIndex && lNumb < rNumb) {
					original.set(k++, left.get(i++));
				}	else {
					original.set(k++, right.get(j++));
				}


		}
		while(i<leftLength) {
			original.set(k++, left.get(i++));
		}
		while(j<rightLength) {
			original.set(k++, right.get(j++));
		}
		
		return original;
	}
	
	public void setServerOrder() {
		String temp = null;
		for(ArrayList<String> list: allInitialInfo) {
			System.out.println("this is the temp value " + temp);
			if(!list.get(0).equals(temp)) {
				System.out.println("the temp value does " + temp + " does not equal the list name " + list.get(0));
				temp = list.get(0);
				sortOrder.add(list.get(0));
			}
		}
	}

}
