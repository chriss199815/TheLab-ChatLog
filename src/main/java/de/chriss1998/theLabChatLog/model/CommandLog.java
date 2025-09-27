package de.chriss1998.theLabChatLog.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class CommandLog {
    public enum SourceType { PLAYER, CONSOLE, RCON, COMMAND_BLOCK, OTHER }

    private Long id;
    private String commandUuid;
    private String serverName;
    private SourceType sourceType;
    private String playerUuid; // nullable
    private String playerName; // nullable
    private String commandText;
    private String worldName; // nullable
    private Double locationX; // nullable
    private Double locationY; // nullable
    private Double locationZ; // nullable
    private boolean cancelled;
    private String metadataJson; // nullable
    private LocalDateTime timestamp;

    public CommandLog() {
        this.commandUuid = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.sourceType = SourceType.OTHER;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCommandUuid() { return commandUuid; }
    public void setCommandUuid(String commandUuid) { this.commandUuid = commandUuid; }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getCommandText() { return commandText; }
    public void setCommandText(String commandText) { this.commandText = commandText; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public Double getLocationX() { return locationX; }
    public void setLocationX(Double locationX) { this.locationX = locationX; }

    public Double getLocationY() { return locationY; }
    public void setLocationY(Double locationY) { this.locationY = locationY; }

    public Double getLocationZ() { return locationZ; }
    public void setLocationZ(Double locationZ) { this.locationZ = locationZ; }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public static class Builder {
        private final CommandLog log = new CommandLog();
        public Builder server(String serverName) { log.serverName = serverName; return this; }
        public Builder source(SourceType type) { log.sourceType = type; return this; }
        public Builder player(String uuid, String name) { log.playerUuid = uuid; log.playerName = name; return this; }
        public Builder command(String text) { log.commandText = text; return this; }
        public Builder world(String world) { log.worldName = world; return this; }
        public Builder location(double x, double y, double z) { log.locationX = x; log.locationY = y; log.locationZ = z; return this; }
        public Builder cancelled(boolean cancelled) { log.cancelled = cancelled; return this; }
        public Builder metadata(String json) { log.metadataJson = json; return this; }
        public CommandLog build() { return log; }
    }
}
