package de.chriss1998.theLabChatLog.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Data model representing a chat message in the database
 */
public class ChatMessage {
    
    public enum MessageType {
        CHAT, PRIVATE, BROADCAST, COMMAND, SYSTEM, JOIN, LEAVE, DEATH, ACHIEVEMENT
    }
    
    private Long id;
    private String messageUuid;
    private String serverName;
    private String worldName;
    private String playerUuid;
    private String playerName;
    private String messageContent;
    private MessageType messageType;
    private String channel;
    private Double locationX;
    private Double locationY;
    private Double locationZ;
    private String recipientUuid;
    private String recipientName;
    private boolean isCancelled;
    private String metadataJson;
    private LocalDateTime timestamp;
    
    // Default constructor
    public ChatMessage() {
        this.messageUuid = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.messageType = MessageType.CHAT;
        this.channel = "global";
        this.isCancelled = false;
    }
    
    // Constructor for chat messages
    public ChatMessage(String serverName, String worldName, String playerUuid, String playerName, 
                      String messageContent, MessageType messageType) {
        this();
        this.serverName = serverName;
        this.worldName = worldName;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.messageContent = messageContent;
        this.messageType = messageType;
    }
    
    // Constructor with location
    public ChatMessage(String serverName, String worldName, String playerUuid, String playerName,
                      String messageContent, MessageType messageType, double x, double y, double z) {
        this(serverName, worldName, playerUuid, playerName, messageContent, messageType);
        this.locationX = x;
        this.locationY = y;
        this.locationZ = z;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getMessageUuid() { return messageUuid; }
    public void setMessageUuid(String messageUuid) { this.messageUuid = messageUuid; }
    
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    
    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    
    public String getMessageContent() { return messageContent; }
    public void setMessageContent(String messageContent) { this.messageContent = messageContent; }
    
    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
    
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    
    public Double getLocationX() { return locationX; }
    public void setLocationX(Double locationX) { this.locationX = locationX; }
    
    public Double getLocationY() { return locationY; }
    public void setLocationY(Double locationY) { this.locationY = locationY; }
    
    public Double getLocationZ() { return locationZ; }
    public void setLocationZ(Double locationZ) { this.locationZ = locationZ; }
    
    public String getRecipientUuid() { return recipientUuid; }
    public void setRecipientUuid(String recipientUuid) { this.recipientUuid = recipientUuid; }
    
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    
    public boolean isCancelled() { return isCancelled; }
    public void setCancelled(boolean cancelled) { isCancelled = cancelled; }
    
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    // Utility methods
    public boolean hasLocation() {
        return locationX != null && locationY != null && locationZ != null;
    }
    
    public boolean isPrivateMessage() {
        return messageType == MessageType.PRIVATE && recipientUuid != null;
    }
    
    public String getLocationString() {
        if (hasLocation()) {
            return String.format("%.2f, %.2f, %.2f", locationX, locationY, locationZ);
        }
        return "Unknown";
    }
    
    // Builder pattern for easy message creation
    public static class Builder {
        private ChatMessage message;
        
        public Builder(String serverName, String playerUuid, String playerName, String content) {
            message = new ChatMessage();
            message.serverName = serverName;
            message.playerUuid = playerUuid;
            message.playerName = playerName;
            message.messageContent = content;
        }
        
        public Builder world(String worldName) {
            message.worldName = worldName;
            return this;
        }
        
        public Builder type(MessageType type) {
            message.messageType = type;
            return this;
        }
        
        public Builder channel(String channel) {
            message.channel = channel;
            return this;
        }
        
        public Builder location(double x, double y, double z) {
            message.locationX = x;
            message.locationY = y;
            message.locationZ = z;
            return this;
        }
        
        public Builder recipient(String recipientUuid, String recipientName) {
            message.recipientUuid = recipientUuid;
            message.recipientName = recipientName;
            return this;
        }
        
        public Builder cancelled(boolean cancelled) {
            message.isCancelled = cancelled;
            return this;
        }
        
        public Builder metadata(String json) {
            message.metadataJson = json;
            return this;
        }
        
        public ChatMessage build() {
            return message;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessage that = (ChatMessage) o;
        return Objects.equals(messageUuid, that.messageUuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(messageUuid);
    }
    
    @Override
    public String toString() {
        return String.format("ChatMessage{uuid='%s', player='%s', type=%s, content='%s', timestamp=%s}",
            messageUuid, playerName, messageType, 
            messageContent.length() > 50 ? messageContent.substring(0, 50) + "..." : messageContent,
            timestamp);
    }
}
