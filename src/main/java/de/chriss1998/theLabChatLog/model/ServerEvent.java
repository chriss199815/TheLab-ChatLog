package de.chriss1998.theLabChatLog.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Data model representing a server event in the database
 */
public class ServerEvent {
    
    public enum EventType {
        SERVER_START, SERVER_STOP, SERVER_RESTART, 
        PLUGIN_LOAD, PLUGIN_UNLOAD, 
        WORLD_LOAD, WORLD_UNLOAD, 
        BACKUP, ERROR, WARNING, INFO
    }
    
    public enum SeverityLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    private Long id;
    private String eventUuid;
    private String serverName;
    private EventType eventType;
    private String eventMessage;
    private SeverityLevel severityLevel;
    private String metadataJson;
    private LocalDateTime timestamp;
    
    // Default constructor
    public ServerEvent() {
        this.eventUuid = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.severityLevel = SeverityLevel.MEDIUM;
    }
    
    // Constructor for events
    public ServerEvent(String serverName, EventType eventType, String eventMessage) {
        this();
        this.serverName = serverName;
        this.eventType = eventType;
        this.eventMessage = eventMessage;
    }
    
    // Constructor with severity
    public ServerEvent(String serverName, EventType eventType, String eventMessage, SeverityLevel severity) {
        this(serverName, eventType, eventMessage);
        this.severityLevel = severity;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getEventUuid() { return eventUuid; }
    public void setEventUuid(String eventUuid) { this.eventUuid = eventUuid; }
    
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    
    public String getEventMessage() { return eventMessage; }
    public void setEventMessage(String eventMessage) { this.eventMessage = eventMessage; }
    
    public SeverityLevel getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(SeverityLevel severityLevel) { this.severityLevel = severityLevel; }
    
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    // Utility methods
    public boolean isCritical() {
        return severityLevel == SeverityLevel.CRITICAL || severityLevel == SeverityLevel.HIGH;
    }
    
    public boolean isErrorRelated() {
        return eventType == EventType.ERROR || eventType == EventType.WARNING;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerEvent that = (ServerEvent) o;
        return Objects.equals(eventUuid, that.eventUuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventUuid);
    }
    
    @Override
    public String toString() {
        return String.format("ServerEvent{uuid='%s', type=%s, severity=%s, message='%s', timestamp=%s}",
            eventUuid, eventType, severityLevel, 
            eventMessage.length() > 100 ? eventMessage.substring(0, 100) + "..." : eventMessage,
            timestamp);
    }
}
