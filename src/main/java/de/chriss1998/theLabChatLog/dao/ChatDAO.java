package de.chriss1998.theLabChatLog.dao;

import de.chriss1998.theLabChatLog.database.DatabaseManager;
import de.chriss1998.theLabChatLog.model.ChatMessage;
import de.chriss1998.theLabChatLog.model.CommandLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for ChatMessage operations
 */
public class ChatDAO {
    
    private final DatabaseManager databaseManager;
    private final Logger logger;
    
    // SQL Queries
    private static final String INSERT_CHAT_MESSAGE = """
        INSERT INTO `chat_messages` (
            message_uuid, server_name, world_name, player_uuid, player_name,
            message_content, message_type, channel, location_x, location_y, location_z,
            recipient_uuid, recipient_name, is_cancelled, metadata_json, `timestamp`
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    
    private static final String SELECT_CHAT_MESSAGES_BY_PLAYER = """
        SELECT * FROM `chat_messages`
        WHERE player_uuid = ?
        ORDER BY `timestamp` DESC
        LIMIT ? OFFSET ?
        """;
    
    private static final String SELECT_CHAT_MESSAGES_BY_TIME_RANGE = """
        SELECT * FROM `chat_messages`
        WHERE `timestamp` BETWEEN ? AND ?
        ORDER BY `timestamp` DESC
        LIMIT ? OFFSET ?
        """;
    
    private static final String SELECT_CHAT_MESSAGES_BY_CONTENT = """
        SELECT * FROM `chat_messages`
        WHERE message_content LIKE ?
        ORDER BY `timestamp` DESC
        LIMIT ? OFFSET ?
        """;
    
    private static final String COUNT_MESSAGES_BY_PLAYER = """
        SELECT COUNT(*) FROM `chat_messages` WHERE player_uuid = ?
        """;
    
    private static final String DELETE_OLD_MESSAGES = """
        DELETE FROM `chat_messages` WHERE `timestamp` < ?
        """;

    // Command logs
    private static final String INSERT_COMMAND_LOG = """
        INSERT INTO `command_logs` (
            command_uuid, server_name, source_type, player_uuid, player_name,
            command_text, world_name, location_x, location_y, location_z,
            is_cancelled, metadata_json, `timestamp`
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String SELECT_COMMAND_LOGS_BY_PLAYER = """
        SELECT * FROM `command_logs`
        WHERE player_uuid = ?
        ORDER BY `timestamp` DESC
        LIMIT ? OFFSET ?
        """;

    // Combined history (CHAT + COMMAND) for a player
    private static final String SELECT_COMBINED_HISTORY_BY_PLAYER = """
        SELECT * FROM (
            SELECT
                'CHAT' AS entry_type,
                cm.`timestamp` AS ts,
                cm.message_content AS text,
                cm.message_type AS subtype,
                cm.world_name AS world_name,
                cm.location_x AS location_x,
                cm.location_y AS location_y,
                cm.location_z AS location_z,
                NULL AS source_type,
                cm.is_cancelled AS is_cancelled
            FROM chat_messages cm
            WHERE cm.player_uuid = ?
            UNION ALL
            SELECT
                'COMMAND' AS entry_type,
                cl.`timestamp` AS ts,
                cl.command_text AS text,
                NULL AS subtype,
                cl.world_name AS world_name,
                cl.location_x AS location_x,
                cl.location_y AS location_y,
                cl.location_z AS location_z,
                cl.source_type AS source_type,
                cl.is_cancelled AS is_cancelled
            FROM command_logs cl
            WHERE cl.player_uuid = ?
        ) t
        ORDER BY ts DESC
        LIMIT ? OFFSET ?
        """;
    
    public ChatDAO(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    /**
     * Insert a command log asynchronously
     */
    public CompletableFuture<Void> insertCommandLogAsync(CommandLog log) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(INSERT_COMMAND_LOG)) {

                statement.setString(1, log.getCommandUuid());
                statement.setString(2, log.getServerName());
                statement.setString(3, log.getSourceType().name());
                statement.setString(4, log.getPlayerUuid());
                statement.setString(5, log.getPlayerName());
                statement.setString(6, log.getCommandText());
                statement.setString(7, log.getWorldName());

                // Nullable location fields
                setDoubleOrNull(statement, 8, log.getLocationX());
                setDoubleOrNull(statement, 9, log.getLocationY());
                setDoubleOrNull(statement, 10, log.getLocationZ());

                statement.setBoolean(11, log.isCancelled());
                statement.setString(12, log.getMetadataJson());
                statement.setTimestamp(13, Timestamp.valueOf(log.getTimestamp()));

                statement.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to insert command log: " + log.getCommandUuid(), e);
            }
        });
    }
    
    /**
     * Insert a chat message asynchronously
     */
    public CompletableFuture<Void> insertChatMessageAsync(ChatMessage message) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(INSERT_CHAT_MESSAGE)) {
                
                statement.setString(1, message.getMessageUuid());
                statement.setString(2, message.getServerName());
                statement.setString(3, message.getWorldName());
                statement.setString(4, message.getPlayerUuid());
                statement.setString(5, message.getPlayerName());
                statement.setString(6, message.getMessageContent());
                statement.setString(7, message.getMessageType().name());
                statement.setString(8, message.getChannel());
                
                // Handle nullable location fields
                if (message.getLocationX() != null) {
                    statement.setDouble(9, message.getLocationX());
                } else {
                    statement.setNull(9, Types.DOUBLE);
                }
                
                if (message.getLocationY() != null) {
                    statement.setDouble(10, message.getLocationY());
                } else {
                    statement.setNull(10, Types.DOUBLE);
                }
                
                if (message.getLocationZ() != null) {
                    statement.setDouble(11, message.getLocationZ());
                } else {
                    statement.setNull(11, Types.DOUBLE);
                }
                
                statement.setString(12, message.getRecipientUuid());
                statement.setString(13, message.getRecipientName());
                statement.setBoolean(14, message.isCancelled());
                statement.setString(15, message.getMetadataJson());
                statement.setTimestamp(16, Timestamp.valueOf(message.getTimestamp()));
                
                statement.executeUpdate();
                
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to insert chat message: " + message.getMessageUuid(), e);
            }
        });
    }
    
    /**
     * Insert multiple chat messages in a batch
     */
    public CompletableFuture<Void> insertChatMessagesBatchAsync(List<ChatMessage> messages) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(INSERT_CHAT_MESSAGE)) {
                
                connection.setAutoCommit(false);
                
                for (ChatMessage message : messages) {
                    statement.setString(1, message.getMessageUuid());
                    statement.setString(2, message.getServerName());
                    statement.setString(3, message.getWorldName());
                    statement.setString(4, message.getPlayerUuid());
                    statement.setString(5, message.getPlayerName());
                    statement.setString(6, message.getMessageContent());
                    statement.setString(7, message.getMessageType().name());
                    statement.setString(8, message.getChannel());
                    
                    // Handle nullable location fields
                    setDoubleOrNull(statement, 9, message.getLocationX());
                    setDoubleOrNull(statement, 10, message.getLocationY());
                    setDoubleOrNull(statement, 11, message.getLocationZ());
                    
                    statement.setString(12, message.getRecipientUuid());
                    statement.setString(13, message.getRecipientName());
                    statement.setBoolean(14, message.isCancelled());
                    statement.setString(15, message.getMetadataJson());
                    statement.setTimestamp(16, Timestamp.valueOf(message.getTimestamp()));
                    
                    statement.addBatch();
                }
                
                statement.executeBatch();
                connection.commit();
                connection.setAutoCommit(true);
                
                logger.info("Successfully inserted " + messages.size() + " chat messages in batch");
                
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to insert chat messages batch", e);
            }
        });
    }
    
    /**
     * Get chat messages by player UUID
     */
    public CompletableFuture<List<ChatMessage>> getChatMessagesByPlayerAsync(String playerUuid, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<ChatMessage> messages = new ArrayList<>();
            
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(SELECT_CHAT_MESSAGES_BY_PLAYER)) {
                
                statement.setString(1, playerUuid);
                statement.setInt(2, limit);
                statement.setInt(3, offset);
                
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        messages.add(mapResultSetToChatMessage(resultSet));
                    }
                }
                
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get chat messages for player: " + playerUuid, e);
            }
            
            return messages;
        });
    }

    /**
     * Get command logs by player UUID
     */
    public CompletableFuture<List<CommandLog>> getCommandLogsByPlayerAsync(String playerUuid, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<CommandLog> logs = new ArrayList<>();
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(SELECT_COMMAND_LOGS_BY_PLAYER)) {

                statement.setString(1, playerUuid);
                statement.setInt(2, limit);
                statement.setInt(3, offset);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        logs.add(mapResultSetToCommandLog(rs));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get command logs for player: " + playerUuid, e);
            }
            return logs;
        });
    }

    /**
     * Get combined history (chat + commands) by player UUID, ordered by timestamp DESC
     */
    public CompletableFuture<List<HistoryEntry>> getCombinedHistoryByPlayerAsync(String playerUuid, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<HistoryEntry> entries = new ArrayList<>();
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(SELECT_COMBINED_HISTORY_BY_PLAYER)) {

                statement.setString(1, playerUuid);
                statement.setString(2, playerUuid);
                statement.setInt(3, limit);
                statement.setInt(4, offset);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        HistoryEntry e = new HistoryEntry();
                        e.entryType = rs.getString("entry_type");
                        e.timestamp = rs.getTimestamp("ts");
                        e.text = rs.getString("text");
                        e.subtype = rs.getString("subtype");
                        e.worldName = rs.getString("world_name");
                        e.locationX = rs.getObject("location_x", Double.class);
                        e.locationY = rs.getObject("location_y", Double.class);
                        e.locationZ = rs.getObject("location_z", Double.class);
                        e.sourceType = rs.getString("source_type");
                        e.cancelled = rs.getBoolean("is_cancelled");
                        entries.add(e);
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get combined history for player: " + playerUuid, e);
            }
            return entries;
        });
    }
    
    /**
     * Get chat messages within a time range
     */
    public CompletableFuture<List<ChatMessage>> getChatMessagesByTimeRangeAsync(
            Timestamp startTime, Timestamp endTime, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<ChatMessage> messages = new ArrayList<>();
            
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(SELECT_CHAT_MESSAGES_BY_TIME_RANGE)) {
                
                statement.setTimestamp(1, startTime);
                statement.setTimestamp(2, endTime);
                statement.setInt(3, limit);
                statement.setInt(4, offset);
                
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        messages.add(mapResultSetToChatMessage(resultSet));
                    }
                }
                
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get chat messages by time range", e);
            }
            
            return messages;
        });
    }
    
    /**
     * Search chat messages by content
     */
    public CompletableFuture<List<ChatMessage>> searchChatMessagesAsync(String searchTerm, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<ChatMessage> messages = new ArrayList<>();
            
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(SELECT_CHAT_MESSAGES_BY_CONTENT)) {
                
                statement.setString(1, "%" + searchTerm + "%");
                statement.setInt(2, limit);
                statement.setInt(3, offset);
                
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        messages.add(mapResultSetToChatMessage(resultSet));
                    }
                }
                
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to search chat messages for: " + searchTerm, e);
            }
            
            return messages;
        });
    }
    
    /**
     * Get message count for a player
     */
    public CompletableFuture<Integer> getMessageCountByPlayerAsync(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(COUNT_MESSAGES_BY_PLAYER)) {
                
                statement.setString(1, playerUuid);
                
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt(1);
                    }
                }
                
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to count messages for player: " + playerUuid, e);
            }
            
            return 0;
        });
    }
    
    /**
     * Delete old messages before a certain timestamp
     */
    public CompletableFuture<Integer> deleteOldMessagesAsync(Timestamp beforeTimestamp) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(DELETE_OLD_MESSAGES)) {
                
                statement.setTimestamp(1, beforeTimestamp);
                int deletedCount = statement.executeUpdate();
                
                logger.info("Deleted " + deletedCount + " old chat messages");
                return deletedCount;
                
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to delete old messages", e);
                return 0;
            }
        });
    }
    
    /**
     * Map ResultSet to ChatMessage object
     */
    private ChatMessage mapResultSetToChatMessage(ResultSet rs) throws SQLException {
        ChatMessage message = new ChatMessage();
        
        message.setId(rs.getLong("id"));
        message.setMessageUuid(rs.getString("message_uuid"));
        message.setServerName(rs.getString("server_name"));
        message.setWorldName(rs.getString("world_name"));
        message.setPlayerUuid(rs.getString("player_uuid"));
        message.setPlayerName(rs.getString("player_name"));
        message.setMessageContent(rs.getString("message_content"));
        message.setMessageType(ChatMessage.MessageType.valueOf(rs.getString("message_type")));
        message.setChannel(rs.getString("channel"));
        
        // Handle nullable location fields
        Double locationX = rs.getObject("location_x", Double.class);
        Double locationY = rs.getObject("location_y", Double.class);
        Double locationZ = rs.getObject("location_z", Double.class);
        
        message.setLocationX(locationX);
        message.setLocationY(locationY);
        message.setLocationZ(locationZ);
        
        message.setRecipientUuid(rs.getString("recipient_uuid"));
        message.setRecipientName(rs.getString("recipient_name"));
        message.setCancelled(rs.getBoolean("is_cancelled"));
        message.setMetadataJson(rs.getString("metadata_json"));
        message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
        
        return message;
    }

    /**
     * Map ResultSet to CommandLog object
     */
    private CommandLog mapResultSetToCommandLog(ResultSet rs) throws SQLException {
        CommandLog log = new CommandLog();
        log.setId(rs.getLong("id"));
        log.setCommandUuid(rs.getString("command_uuid"));
        log.setServerName(rs.getString("server_name"));
        String src = rs.getString("source_type");
        if (src != null) {
            try { log.setSourceType(CommandLog.SourceType.valueOf(src)); } catch (IllegalArgumentException ignored) {}
        }
        log.setPlayerUuid(rs.getString("player_uuid"));
        log.setPlayerName(rs.getString("player_name"));
        log.setCommandText(rs.getString("command_text"));
        log.setWorldName(rs.getString("world_name"));
        log.setLocationX(rs.getObject("location_x", Double.class));
        log.setLocationY(rs.getObject("location_y", Double.class));
        log.setLocationZ(rs.getObject("location_z", Double.class));
        log.setCancelled(rs.getBoolean("is_cancelled"));
        log.setMetadataJson(rs.getString("metadata_json"));
        Timestamp ts = rs.getTimestamp("timestamp");
        if (ts != null) {
            log.setTimestamp(ts.toLocalDateTime());
        }
        return log;
    }
    
    /**
     * Helper method to set double or null
     */
    private void setDoubleOrNull(PreparedStatement statement, int parameterIndex, Double value) throws SQLException {
        if (value != null) {
            statement.setDouble(parameterIndex, value);
        } else {
            statement.setNull(parameterIndex, Types.DOUBLE);
        }
    }

    /**
     * Simple DTO for combined history entries
     */
    public static class HistoryEntry {
        public String entryType; // CHAT or COMMAND
        public Timestamp timestamp;
        public String text;
        public String subtype; // ChatMessage.MessageType as string (for CHAT)
        public String worldName;
        public Double locationX;
        public Double locationY;
        public Double locationZ;
        public String sourceType; // CommandLog.SourceType as string (for COMMAND)
        public boolean cancelled;
    }
}

