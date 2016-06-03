package server;
import java.time.ZonedDateTime;

public class Session {
	private String sessionID;
	private int version;
	private String message;
	private ZonedDateTime discard_time;
	
	private static final String DEFAULT_MESSAGE = "Hello User!";
	
	public Session(String sessionID, int version, ZonedDateTime discard_time) {
		this.sessionID = sessionID;
		this.version = version;
		this.discard_time = discard_time;
		this.message = DEFAULT_MESSAGE;
	}
	
	public Session(String serverId, int reboot_num, int session_num) {
		StringBuilder sb = new StringBuilder();
		sb.append(serverId);
		sb.append("#");
		sb.append(reboot_num);
		sb.append("#");
		sb.append(session_num);
		this.sessionID = sb.toString();
		this.version = 1;
		this.message = DEFAULT_MESSAGE;
	}
	
	public String getSessionID() {
		return sessionID;
	}
	public void setDiscardTime(int seconds) {
		this.discard_time = ZonedDateTime.now().plusSeconds(seconds);
	}
	
	public ZonedDateTime getDiscardTime() {
		return discard_time;
	}
	
	public String getMessage() {
		return message;
	}
	
	public void setMessage(String message) {
		if(message.length() > 256) {
			this.message = message.substring(0, 256);
		}
		else this.message = message;
	}
	
	public int getVersion() {
		return version;
	}
	
	public boolean expir() {
		return this.discard_time.isBefore(ZonedDateTime.now());
	}
	
	public String getkey() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.sessionID);
		sb.append("|");
		sb.append(this.version);
		return sb.toString();
	}
}
