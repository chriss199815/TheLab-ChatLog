package de.chriss1998.theLabChatLog.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Data model representing a player session in the database
 */
public class PlayerSession {
    
    private Long id;
    private String sessionUuid;
    private String playerUuid;
    private String playerName;
    private String serverName;
    private LocalDateTime loginTime;
    private LocalDateTime logoutTime;
    private String ipAddress;
    private String clientBrand;
    
    // Default constructor
    public PlayerSession() {
        this.sessionUuid = UUID.randomUUID().toString();
        this.loginTime = LocalDateTime.now();
    }
    
    // Constructor for new session
    public PlayerSession(String playerUuid, String playerName, String serverName) {
        this();
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.serverName = serverName;
    }
    
    // Constructor with IP address
    public PlayerSession(String playerUuid, String playerName, String serverName, String ipAddress) {
        this(playerUuid, playerName, serverName);
        this.ipAddress = ipAddress;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getSessionUuid() { return sessionUuid; }
    public void setSessionUuid(String sessionUuid) { this.sessionUuid = sessionUuid; }
    
    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    
    public LocalDateTime getLoginTime() { return loginTime; }
    public void setLoginTime(LocalDateTime loginTime) { this.loginTime = loginTime; }
    
    public LocalDateTime getLogoutTime() { return logoutTime; }
    public void setLogoutTime(LocalDateTime logoutTime) { this.logoutTime = logoutTime; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getClientBrand() { return clientBrand; }
    public void setClientBrand(String clientBrand) { this.clientBrand = clientBrand; }
    
    // Utility methods
    public boolean isActive() {
        return logoutTime == null;
    }
    
    public long getSessionDurationMinutes() {
        LocalDateTime endTime = logoutTime != null ? logoutTime : LocalDateTime.now();
        return java.time.Duration.between(loginTime, endTime).toMinutes();
    }
    
    public void endSession() {
        this.logoutTime = LocalDateTime.now();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerSession that = (PlayerSession) o;
        return Objects.equals(sessionUuid, that.sessionUuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sessionUuid);
    }
    
    @Override
    public String toString() {
        return String.format("PlayerSession{uuid='%s', player='%s', server='%s', login=%s, active=%s}",
            sessionUuid, playerName, serverName, loginTime, isActive());
    }
}
