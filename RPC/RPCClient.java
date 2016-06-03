package RPC;

import java.io.IOException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * RPC client code
 * @author zhuchongwei
 *
 */
public class RPCClient {
	
	private static final int PORTPROJ1BRPC = 5300;
	private static final int MAXSIZE = 512;
	private static final String DELIMETER = "|";
	private static final String RECOVER = "\\|";
	private static final int TIMEOUT = 2000;
	
	/**
	 * send a packet from the servers to read the session, keeps running until one response get
	 * @param callID
	 * @param searchKey
	 * @param addrs
	 * @return
	 */
	public static DatagramPacket sessionReadClient(String[] found, String callID, String searchKey, List<InetAddress> addrs) {
		DatagramPacket recvPkt = null;
		DatagramSocket rpcSocket = null;
		try {
			rpcSocket = new DatagramSocket(5301);
			rpcSocket.setSoTimeout(TIMEOUT);
			int operationCode = 0;

			
			for(InetAddress destAddr : addrs) {
				
				StringBuilder sb = new StringBuilder();
				sb.append(callID);
				sb.append(DELIMETER);
				sb.append(operationCode);
				sb.append(DELIMETER);
				sb.append(searchKey);
				sb.append(DELIMETER);
				sb.append(destAddr.toString());
				byte[] outBuf = sb.toString().getBytes();
				
				DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length, destAddr, PORTPROJ1BRPC);
				rpcSocket.send(sendPkt);
				System.out.println("Reading from " + destAddr.toString());
			}
			
			byte[] inBuf = new byte[MAXSIZE];
			recvPkt = new DatagramPacket(inBuf, inBuf.length);
			String[] tmp = null;
			do {
				System.out.println("Receiving reply");
				
				recvPkt.setLength(inBuf.length);
				rpcSocket.receive(recvPkt);
				
//				System.out.println("Getting " + new String(recvPkt.getData()));
				tmp = new String(recvPkt.getData(), 0, recvPkt.getLength()).split(RECOVER);
				
				if(tmp.length == 4) found[0] = tmp[3];		
			} while(!(tmp.length == 4 && tmp[0].equals(callID)));
			
			System.out.println("Received Data");
			
		} catch (SocketException e) {
			recvPkt = null;
//			sessionReadClient(callID, searchKey,addrs);
		} catch (SocketTimeoutException e) {	
			recvPkt = null;
		} catch (IOException e) {
			sessionReadClient(found, callID, searchKey,addrs);
		}
		
		if(rpcSocket != null) rpcSocket.close();
		return recvPkt;
	}
	
	/**
	 * write to the server
	 * @param callID
	 * @param searchKey
	 * @param message
	 * @param addr
	 * @param discard_time
	 * @return true if succeed
	 */
	public static boolean sessionWriteClient(String callID, String searchKey, String message, InetAddress addr, ZonedDateTime discard_time) {
		DatagramSocket rpcSocket = null;
		DatagramPacket recvPkt = null;
		int operationCode = 1;
		try {
			rpcSocket = new DatagramSocket(5301);
			rpcSocket.setSoTimeout(TIMEOUT);
			StringBuilder sb = new StringBuilder();
			sb.append(callID);
			sb.append(DELIMETER);
			sb.append(operationCode);
			sb.append(DELIMETER);
			sb.append(searchKey);
			sb.append(DELIMETER);
			sb.append(discard_time.toString());
			sb.append(DELIMETER);
			sb.append(message);
			byte[] outBuf = sb.toString().getBytes();	
			
			
			DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length, addr, PORTPROJ1BRPC);
			System.out.println("Writing to " + addr.toString());
			rpcSocket.send(sendPkt);
			
			byte[] inBuf = new byte[MAXSIZE];
			recvPkt = new DatagramPacket(inBuf, inBuf.length);
			
			do {
				System.out.println("Receiving reply");
				
				recvPkt.setLength(MAXSIZE);
				rpcSocket.receive(recvPkt);
				
				System.out.println("Getting " + new String(recvPkt.getData()));
				
			} while(!new String(recvPkt.getData(), 0, recvPkt.getLength()).split(RECOVER)[0].equals(callID));
			System.out.println("Received Data");
		} catch (SocketException e) {
			recvPkt = null;
//			sessionWriteClient(callID, searchKey, message, addr, discard_time);
		} catch (SocketTimeoutException e) {	
			recvPkt = null;
		} catch (IOException e) {
			sessionWriteClient(callID, searchKey, message, addr, discard_time);
		}
		
		if(rpcSocket != null) rpcSocket.close();
		return recvPkt != null;
	}
}
