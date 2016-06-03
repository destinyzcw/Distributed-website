package server;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import RPC.RPCClient;
import RPC.RPCServer;

/**
 * handles all the executions of the servlet
 * @author zhuchongwei
 *
 */
public class SessionHandler {
	
	private static final String COOKIE_NAME = "CS5300PROJ1SESSION";
	private static final String DELIMETER = "|";
	private static final String RECOVER = "\\|";
	private int reboot_num;
	private String serverId;
	private int session_num;
	private Map<String, Session> sessionPool;
	private RPCServer RPCServer;
	private Map<String, InetAddress> servers;
	private PriorityQueue<Session> pq;
	
	private static final int SESSION_TIMEOUT_SECS = 180;
	private static final int DELTA = 10;
	
	//set W = n, R = WQ
	private int WQ;
	private int callID;
	
	/**
	 * garbage collection thread
	 * @author zhuchongwei
	 *
	 */
	class GarbageCollection extends Thread {
		public void run() {
			while(true) {
				try {
					if(!pq.isEmpty()){
						while(!pq.isEmpty() && pq.peek().expir()) {
							sessionPool.remove(pq.poll().getkey());
							System.out.println("garbage collection...");
						}
					}
	                Thread.sleep(1000);
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
			}
		}
	}
	
	public SessionHandler(String serverId, int reboot_num, Map<String, InetAddress> servers, int f) {
		this.reboot_num = reboot_num;
		this.serverId = serverId;
		this.servers = servers;
		this.session_num = 0;
		this.sessionPool = new ConcurrentHashMap<>();
		this.callID = 1;
		
		int n = servers.keySet().size();
		if(2 * f + 1 > n) {
			this.WQ = (n - 1) / 2;
		}
		else this.WQ = f + 1;
		
		//garbage collection
    	pq = new PriorityQueue<Session>(new Comparator<Session>() {

			@Override
			public int compare(Session s1, Session s2) {
				if(s1.getDiscardTime().isBefore(s2.getDiscardTime())) return -1;
				else if (s1.getDiscardTime().isAfter(s2.getDiscardTime())) return 1;
				else return 0;
			}
    		
    	});
		this.RPCServer = new RPCServer(sessionPool, pq);
		this.RPCServer.start();
		
    	Thread t = new GarbageCollection();
    	t.start();
	}
	
	/**
	 * get the cookie from the request, if no meets requirement create one 
	 * @param request
	 * @param response
	 * @return
	 */
	public Cookie getCookie(HttpServletRequest request, HttpServletResponse response) {
		Cookie[] cookies = request.getCookies();
		Cookie cookie = null;
		if(cookies != null) {
			for(Cookie tmp : cookies) {
				if(tmp.getName().equals(COOKIE_NAME)) cookie = tmp;
			}
		}
//		if(cookie == null) {
//			cookie = createCookie();
//			response.addCookie(cookie);
//		}
		return cookie;
	}
	
	/**
	 * get the session from the serverlist of cookie, if no one found, create one
	 * @param cookie
	 * @param request
	 * @param response
	 * @return
	 */
	public Session getSession(String[] found, Cookie cookie, HttpServletRequest request, HttpServletResponse response) {
		String key = cookie.getValue();
		System.out.println(key);
		String[] names = key.split(RECOVER);
		String sessionId = names[0];
		int version = Integer.parseInt(names[1]);
		List<InetAddress> storeServers = new ArrayList<>();
		for(int i = 2; i < names.length; i++) {
			if((servers.get(names[i])) != null) 
				storeServers.add(servers.get(names[i]));
		}
		
		if(sessionPool.get(cookie.getValue()) != null) {
			found[0] = this.serverId;
			return sessionPool.get(cookie.getValue());
		}
		else {
			
			DatagramPacket packet = RPCClient.sessionReadClient(found, String.valueOf(callID), key, storeServers);
			callID++;
			if(packet == null) return null;
			if(packet == null || new String(packet.getData()).split(RECOVER).length != 4) {
				//don't have that session
				return null;
			}
			else {
				if(found[0] != null) {
					for(Entry<String, InetAddress> e : servers.entrySet()) {
						if(e.getValue().equals(found[0])) {
							found[0] = e.getKey();
							break;
						}
					}
				}
				String[] data = new String(packet.getData()).split(RECOVER);
				ZonedDateTime discard_time = ZonedDateTime.parse(data[1]);
				String message = data[2];
				Session session = new Session(sessionId, version, discard_time);
				session.setMessage(message);
				return session;
			}			
		}
	}
	
	/**
	 * handles replace request: generate a new session and write to servers
	 * @param prevSession
	 * @param request
	 * @param response
	 * @return
	 */
	public Session replace(Session prevSession, HttpServletRequest request, HttpServletResponse response) {
		
		String sessionId = prevSession.getSessionID();
		int version = prevSession.getVersion() + 1;
		String message = request.getParameter("username");
		Cookie cookie = updateCookie(request);
		ZonedDateTime discard_time = ZonedDateTime.now().plusSeconds(SESSION_TIMEOUT_SECS + DELTA);
		Session currSession = new Session(sessionId, version, discard_time);
		currSession.setMessage(message);
		List<String> storeServers = sendSession(currSession);
		updateCookie(cookie, request, storeServers);
		response.addCookie(cookie);
		return currSession;
		
	}
	
	/**
	 * handles refresh request: generate a new session and write to servers
	 * @param prevSession
	 * @param request
	 * @param response
	 * @return
	 */
	public Session refresh(Session prevSession, HttpServletRequest request, HttpServletResponse response) {
		
		String sessionId = prevSession.getSessionID();
		int version = prevSession.getVersion() + 1;
		Cookie cookie = updateCookie(request);
		ZonedDateTime discard_time = ZonedDateTime.now().plusSeconds(SESSION_TIMEOUT_SECS + DELTA);
		Session currSession = new Session(sessionId, version, discard_time);
		currSession.setMessage(prevSession.getMessage());
		List<String> storeServers = sendSession(currSession);
		updateCookie(cookie, request, storeServers);
		response.addCookie(cookie);
		return currSession;
	}	
	
	/**
	 * update the maxAge of cookie
	 * @param request
	 * @return
	 */
	private Cookie updateCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		Cookie cookie = null;
		if(cookies != null) {
			for(Cookie tmp : cookies) {
				if(tmp.getName().equals(COOKIE_NAME)) cookie = tmp;
			}
		}
		if(cookie != null) {
			cookie.setMaxAge(SESSION_TIMEOUT_SECS);
		}
		return cookie;
	}
	
	/**
	 * update the value of the cookie
	 * @param cookie
	 * @param request
	 * @param storeServers
	 */
	private void updateCookie(Cookie cookie, HttpServletRequest request, List<String> storeServers) {

		if(cookie != null) {

			String[] names = cookie.getValue().split(RECOVER);
			StringBuilder sb = new StringBuilder();
			sb.append(names[0]);
			sb.append(DELIMETER);
			sb.append(Integer.parseInt(names[1]) + 1);
			for(String server: storeServers) {
				sb.append(DELIMETER);
				sb.append(server);
			}
			cookie.setDomain(".xz479.bigdata.systems");
			cookie.setPath("/cs5300_project1b");
			cookie.setValue(sb.toString());
		}
	}
	
	/**
	 * create a new cookie with creating a new session
	 * @return
	 */
	public Cookie createCookie(String message) {
		Session session = new Session(serverId, reboot_num, session_num++);
		if(message != null) session.setMessage(message);
		session.setDiscardTime(SESSION_TIMEOUT_SECS + DELTA);
		sessionPool.put(session.getkey(), session);
		List<String> serverList = sendSession(session);
		StringBuilder sb = new StringBuilder();
		sb.append(session.getkey());
		for(String server : serverList) {
			sb.append(DELIMETER);
			sb.append(server);
		}
		Cookie cookie = new Cookie(COOKIE_NAME, sb.toString());
		cookie.setMaxAge(SESSION_TIMEOUT_SECS);
		cookie.setDomain(".xz479.bigdata.systems");
		cookie.setPath("/cs5300_project1b");
		return cookie;
	}
	
	/**
	 * send the new session to random selected servers
	 * @param currSession
	 * @return
	 */
	private List<String> sendSession(Session currSession) {
		List<String> sendList = new ArrayList<>();
		List<String> storeList = new ArrayList<>();
		for(String addr : servers.keySet()) {
			sendList.add(addr);
		}
		int count = 0;
		Random rand = new Random();
		while(count < WQ) {
			int index = rand.nextInt(sendList.size());
			boolean flag = RPCClient.sessionWriteClient(String.valueOf(callID), currSession.getkey(), currSession.getMessage(), servers.get(sendList.get(index)), currSession.getDiscardTime());
			callID++;
			if(flag) {
				storeList.add(sendList.get(index));
				sendList.remove(index);
				count++;
			}
		}
		return storeList;
	}
}
