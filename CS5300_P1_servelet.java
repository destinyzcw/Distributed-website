

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import server.Session;
import server.SessionHandler;

/**
 * Servlet implementation class CS5300_P1_servelet
 */
@WebServlet("/CS5300_P1_servelet")
public class CS5300_P1_servelet extends HttpServlet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private SessionHandler sessionHandler;
	
	private String serverId;
	
	private int reboot_num;
	
	private int f;
	
	private String filePath;
	private String server_data = "../output.txt";
	private String serverId_data = "../index.txt";
	private String reboot_data = "../reboot.txt";
	private String f_data = "../f.txt";
	
	Map<String, InetAddress> servers = new ConcurrentHashMap<>();
	
	
    /**
     * Default constructor. 
     * @throws IOException 
     */
    public CS5300_P1_servelet() throws IOException {
		super();
    }
    
    @Override
    public void init(ServletConfig config) throws ServletException {
    	super.init(config);
    	this.filePath = getServletContext().getRealPath("/");
		try {
			readData();
		    sessionHandler = new SessionHandler(serverId, reboot_num, servers, f);
		} catch (IOException e) {
			System.out.println("File not found !");
		}
    }
    

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		PrintWriter out = response.getWriter();
		String[] found = new String[1];
		Cookie cookie = sessionHandler.getCookie(request, response);
		if(cookie == null) {
			cookie = sessionHandler.createCookie(null);
			response.addCookie(cookie);
			Session session = sessionHandler.getSession(found, cookie, request, response);
			printHtml(out, session, cookie, found);
		}
		else {
			Session session = sessionHandler.getSession(found, cookie, request, response);
			if(session == null) {
				printError(out);
				return;
			}
			Session currSession = sessionHandler.refresh(session, request, response);
			printHtml(out, currSession, cookie, found);
		}

	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		String[] found = new String[1];
		
		Cookie cookie = sessionHandler.getCookie(request, response);
		if(request.getParameter("replace") != null) {
			if(cookie == null) {
				cookie = sessionHandler.createCookie(request.getParameter("username"));
				response.addCookie(cookie);
				Session session = sessionHandler.getSession(found, cookie, request, response);
				printHtml(out, session, cookie, found);
			}
			else {
				Session session = sessionHandler.getSession(found, cookie, request, response);
				if(session == null) {
					printError(out);
					return;
				}
				Session currSession = sessionHandler.replace(session, request, response);
				printHtml(out, currSession, cookie, found);
			}

		}
		else if (request.getParameter("refresh") != null) {
			if(cookie == null) {
				cookie = sessionHandler.createCookie(null);
				response.addCookie(cookie);
				Session session = sessionHandler.getSession(found, cookie, request, response);
				printHtml(out, session, cookie, found);
			}
			else {
				Session session = sessionHandler.getSession(found, cookie, request, response);
				if(session == null) {
					printError(out);
					return;
				}
				Session currSession = sessionHandler.refresh(session, request, response);
				printHtml(out, currSession, cookie, found);
			}
		}
		else if (request.getParameter("logout") != null) {
			cookie.setDomain(".xz479.bigdata.systems");
			cookie.setPath("/cs5300_project1b");
			cookie.setMaxAge(0);
			response.addCookie(cookie);
			printLogout(out);
		}
	}

	
	
	/**
	 * print the html corresponds to the user cookie
	 * @param out
	 * @param session
	 */
	private void printHtml(PrintWriter out, Session session, Cookie cookie, String[] found) {
		String curTime = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME);
		String sessionID = session == null ? "" : session.getSessionID();
		String sessionVersion = session == null ? "" : String.valueOf(session.getVersion());
		String sessionMessage = session == null ? "" : session.getMessage();
		String sessionExpir = session == null ? "" : session.getDiscardTime().format(DateTimeFormatter.RFC_1123_DATE_TIME);
		String cookieValue = cookie == null ? "" : cookie.getValue();
		String cookieDomain = cookie == null ? "" : cookie.getDomain();
		String metaData = "";
		if(cookie != null) {
			String[] tmp = cookieValue.split("\\|");
			for(int i = 2; i < tmp.length; i++) {
				metaData += tmp[i] + "|";
			}
		}
		out.println("<html>");
	    out.println("<head>");
	    out.println("<title>CS5300_P1</title>");
	    out.println("</head>");
	    out.println("<body>");
	    out.println("<h>NetID: cz344</h>");
	    out.println("<h>Session: " + sessionID + "</h>");
	    out.println("<h>Version: " + sessionVersion + "</h>");
	    out.println("<h>Date: " + curTime + "</h>");
	    out.println("<h>Server: " + this.serverId + "</h>");
	    out.println("<h>Reboot#: " + this.reboot_num + "</h>");
	    out.println("<h>Found at Server : " + found[0] + "</h><br>");
	    out.println("<b>" + sessionMessage + "</b>");
	    out.println("<form method='post'>");
	    out.println("<input type='submit' name='replace' value='Replace'>");
	    out.println("<input type='text' name='username'><br>");
	    out.println("<input type='submit' name='refresh' value='Refresh'><br>");
	    out.println("<input type='submit' name='logout' value='Logout'><br>");
	    out.println("</form><br>");
	    out.println("<h>Cookie: " + cookieValue + "</h>");
	    out.println("<h>Cookie metaData: " + metaData + "</h>");
	    out.println("<h>Cookie Domain: " + cookieDomain + "</h>");
	    out.println("</h>Expires: " + sessionExpir + "</h>");
	    out.println("</body>");
	    out.println("</html>");
	}
	/**
	 * print the error page
	 * @param out
	 */
	private void printError(PrintWriter out) {
		out.println("<html>");
	    out.println("<head>");
	    out.println("<title>CS5300_P1</title>");
	    out.println("</head>");
	    out.println("<body>");
	    out.println("<h>Session cannot be found !</h>");
	    out.println("</body>");
	    out.println("</html>");
	}
	/**
	 * print the logout page
	 * @param out
	 */
	private void printLogout(PrintWriter out) {
		out.println("<html>");
	    out.println("<head>");
	    out.println("<title>CS5300_P1</title>");
	    out.println("</head>");
	    out.println("<body>");
	    out.println("<h>You have successfully logout !</h>");
	    out.println("</body>");
	    out.println("</html>");
	}
	
	/**
	 * read all the data files in the instance
	 * @throws IOException
	 */
	private void readData() throws IOException{
		
    	
		String content = new String(Files.readAllBytes(Paths.get(filePath + server_data)));
		
		String instanceID = new String(Files.readAllBytes(Paths.get(filePath + serverId_data)));
		String rebootID = new String(Files.readAllBytes(Paths.get(filePath + reboot_data)));
		String f_num = new String(Files.readAllBytes(Paths.get(filePath + f_data)));
		
		String regex = "[0-9]+";
    	Matcher m1 = Pattern.compile(regex).matcher(rebootID);
    	
    	if(m1.find()) {
    		rebootID = m1.group();
    	}
    	
    	m1 = Pattern.compile(regex).matcher(instanceID);
     	
     	if(m1.find()) {
     		instanceID = m1.group();
     	}
     	
     	m1 = Pattern.compile(regex).matcher(f_num);
     	
     	if(m1.find()) {
     		f_num = m1.group();
     	}
     	this.f = Integer.parseInt(f_num);
    	
		this.serverId = instanceID;
		this.reboot_num = Integer.parseInt(rebootID);
		
		   String input = "[" + content + "]";
		
	      JSONParser parser = new JSONParser();
	      
	      try{
	         Object obj = parser.parse(input);
	         JSONArray array = (JSONArray)obj;		
	     
	         JSONObject obj2 = (JSONObject)array.get(0);
	         JSONArray arr = (JSONArray)obj2.get("Items");

	         int num = arr.size();

	         for(int i = 0; i < num; i++) {
	        	 JSONObject objAttrName = (JSONObject)arr.get(i);
	        	 JSONArray arrAttrName = (JSONArray)objAttrName.get("Attributes");
	        	 // Get index
	        	 JSONObject objIndex = (JSONObject)arrAttrName.get(0);
	        	 String index = (String)objIndex.get("Value");
	        	 // Get ip
	        	 JSONObject objIp = (JSONObject)arrAttrName.get(1);
	        	 String ip = (String)objIp.get("Value");
	        	 
	        	 InetAddress addr = InetAddress.getByName(ip);

	        	 this.servers.put(index, addr);
	         }
	         
	      }catch(ParseException pe){
	         System.out.println("position: " + pe.getPosition());
	         System.out.println(pe);
	      } 
	}
	
}
