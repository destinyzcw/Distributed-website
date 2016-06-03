package RPC;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.ZonedDateTime;
import java.util.Hashtable;
import java.util.Map;
import java.util.PriorityQueue;

import server.Session;

/**
 * RPC server code
 * @author zhuchongwei
 *
 */
public class RPCServer extends Thread{
	
	private DatagramSocket rpcSocket;
	private static final int PORTPROJ1BRPC = 5300;
	private final String DELIMETER = "|";
	private final String RECOVER = "\\|";
	private Map<String, Session> sessionPool;
	private PriorityQueue<Session> pq;
	
	public RPCServer(Map<String, Session> sessionPool, PriorityQueue<Session> pq) {
		System.out.println("Starting RPC Server...");
		try {		
			rpcSocket = new DatagramSocket(PORTPROJ1BRPC);
			rpcSocket.setSoTimeout(10000);
			this.sessionPool = sessionPool;
			this.pq = pq;
	    	
		} catch (SocketException e) {
//			e.printStackTrace();
		} 

	}
	
	/**
	 * keeps listen to the port and receive packet
	 */
	public void run() {
		while(true){
		try {

			System.out.println("Receiving client packet...");
			byte[] data = new byte[512];
			DatagramPacket clientPacket = new DatagramPacket(data, data.length);
			rpcSocket.receive(clientPacket);
			
			InetAddress clientAddress = clientPacket.getAddress();
			int clientPort = clientPacket.getPort();
			
			String[] request = new String(data).split(RECOVER);
			if(request.length < 4) {
				System.out.println("unknown request!");
				return;
			}
			String callID = request[0];
			
			int operationCode = Integer.parseInt(request[1]);
			String sessionID = request[2];
			int version = Integer.parseInt(request[3]);

			byte[] outBuf = null;
			
			switch(operationCode) {
			case 0 : 
				String address = request[4];
				outBuf = sessionRead(callID, sessionID, version, address);
				break;
			case 1:
				ZonedDateTime discard_time = ZonedDateTime.parse(request[4]);
				String message = request[5];
				outBuf = sessionWrite(callID, sessionID, version, discard_time, message);
				break;
			}
				
			if(outBuf == null) return;
			DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length,
			    	clientAddress, clientPort);
			
			System.out.println("Sending back to " + clientAddress.toString());
			
			rpcSocket.send(sendPkt);
			
			System.out.println("Session List :");
			for(String sessionId : sessionPool.keySet()) {
				System.out.println(sessionId);
			}
			System.out.println("Session List Finished");
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
		}
		
		
		}
	}
	
	/**
	 * read the session if it stores in the server, else contain empty message
	 * @param callID
	 * @param sessionID
	 * @param version
	 * @return
	 */
	private byte[] sessionRead(String callID, String sessionID, int version, String address) {
		Session session = getSession(sessionID, version);
		String message = "";
		if(session != null) message = session.getMessage();
		StringBuilder out = new StringBuilder();
		out.append(callID);
		if(session == null) return out.toString().getBytes();
		out.append(DELIMETER);
		out.append(session.getDiscardTime().toString());
		out.append(DELIMETER);
		out.append(message);
		out.append(DELIMETER);
		out.append(address);
		return out.toString().getBytes();
	}
	
	/**
	 * write the session to this server
	 * @param callID
	 * @param sessionID
	 * @param version
	 * @param discard_time
	 * @param message
	 * @return
	 */
	private byte[] sessionWrite(String callID, String sessionID, int version, ZonedDateTime discard_time, String message) {
		
		Session	session = new Session(sessionID, version, discard_time);
		session.setMessage(message);
		sessionPool.put(session.getkey(), session);
		pq.offer(session);
		StringBuilder out = new StringBuilder();
		out.append(callID);
		out.append(DELIMETER);
		out.append("0");
		return out.toString().getBytes();
	}
	
	/**
	 * get the session from the sessionPool
	 * @param sessionID
	 * @param version
	 * @return
	 */
	private Session getSession(String sessionID, int version) {
		StringBuilder keyBuilder = new StringBuilder();
		keyBuilder.append(sessionID);
		keyBuilder.append(DELIMETER);
		keyBuilder.append(version);
		String searchKey = keyBuilder.toString();
		return sessionPool.get(searchKey.toString());
	}
}
