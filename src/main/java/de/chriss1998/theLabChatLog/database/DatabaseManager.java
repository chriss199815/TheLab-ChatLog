package de.chriss1998.theLabChatLog.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.chriss1998.theLabChatLog.TheLabChatLog;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connections using HikariCP connection pooling
 * Handles both MySQL and MariaDB connections
 */
public class DatabaseManager {
    
    private final TheLabChatLog plugin;
    private final Logger logger;
    private HikariDataSource dataSource;
    private ExecutorService asyncExecutor;
    private boolean isEnabled = false;
    
    // Database configuration
    private String databaseType;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private boolean sslEnabled;
    
    public DatabaseManager(TheLabChatLog plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.asyncExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "ChatLog-Database-Thread");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    /**
     * Initialize the database connection
     */
    public boolean initialize() {
        try {
            loadConfiguration();
            // Ensure the configured database/schema exists before creating the pool
            ensureDatabaseExists();
            setupHikariDataSource();
            testConnection();
            // Ensure required tables and views exist
            ensureSchema();
            isEnabled = true;
            logger.info("DatabaseManager successfully initialized with " + databaseType.toUpperCase());
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize DatabaseManager", e);
            return false;
        }
    }
    
    /**
     * Load database configuration from config.yml
     */
    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        
        this.databaseType = config.getString("database.type", "mysql").toLowerCase();
        this.host = config.getString("database.host", "localhost");
        this.port = config.getInt("database.port", 3306);
        this.database = config.getString("database.database", "thelab_chatlog");
        this.username = config.getString("database.username", "app");
        this.password = config.getString("database.password", "");
        this.sslEnabled = config.getBoolean("database.ssl.enabled", false);
        
        logger.info(String.format("Database config loaded: %s://%s:%d/%s", 
            databaseType, host, port, database));
    }
    
    /**
     * Setup HikariCP connection pool
     */
    private void setupHikariDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        
        // Build JDBC URL based on database type
        String jdbcUrl;
        if ("mariadb".equals(databaseType)) {
            hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
            jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s", host, port, database);
        } else {
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            jdbcUrl = String.format("jdbc:mysql://%s:%d/%s", host, port, database);
        }
        
        // Add connection parameters
        StringBuilder urlBuilder = new StringBuilder(jdbcUrl);
        urlBuilder.append("?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
        
        if (!sslEnabled) {
            if ("mariadb".equals(databaseType)) {
                urlBuilder.append("&useSSL=false");
            } else {
                urlBuilder.append("&useSSL=false&allowPublicKeyRetrieval=true");
            }
        }
        
        hikariConfig.setJdbcUrl(urlBuilder.toString());
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        
        // Connection pool configuration from config.yml
        FileConfiguration config = plugin.getConfig();
        hikariConfig.setMaximumPoolSize(config.getInt("database.pool.maximum_pool_size", 10));
        hikariConfig.setMinimumIdle(config.getInt("database.pool.minimum_idle", 2));
        hikariConfig.setConnectionTimeout(config.getLong("database.pool.connection_timeout", 30000));
        hikariConfig.setIdleTimeout(config.getLong("database.pool.idle_timeout", 600000));
        hikariConfig.setMaxLifetime(config.getLong("database.pool.max_lifetime", 1800000));
        hikariConfig.setLeakDetectionThreshold(config.getLong("database.pool.leak_detection_threshold", 60000));
        
        // Connection validation
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setValidationTimeout(3000);
        
        // Pool name for monitoring
        hikariConfig.setPoolName("TheLab-ChatLog-HikariCP");
        
        this.dataSource = new HikariDataSource(hikariConfig);
        
        logger.info("HikariCP connection pool configured with " + 
            hikariConfig.getMaximumPoolSize() + " max connections");
    }
    
    /**
     * Test the database connection
     */
    private void testConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) {
                throw new SQLException("Connection validation failed");
            }
            logger.info("Database connection test successful");
        }
    }

    /**
     * Ensure that the configured database/schema exists. If it doesn't, create it.
     * This connects without specifying a schema and runs a CREATE DATABASE IF NOT EXISTS.
     */
    private void ensureDatabaseExists() throws Exception {
        // Build a server-level JDBC URL without a schema (database name)
        String baseUrl;
        if ("mariadb".equals(databaseType)) {
            Class.forName("org.mariadb.jdbc.Driver");
            baseUrl = String.format("jdbc:mariadb://%s:%d/", host, port);
        } else {
            Class.forName("com.mysql.cj.jdbc.Driver");
            baseUrl = String.format("jdbc:mysql://%s:%d/", host, port);
        }

        StringBuilder url = new StringBuilder(baseUrl);
        url.append("?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
        if (!sslEnabled) {
            if ("mariadb".equals(databaseType)) {
                url.append("&useSSL=false");
            } else {
                url.append("&useSSL=false&allowPublicKeyRetrieval=true");
            }
        }

        try (Connection conn = DriverManager.getConnection(url.toString(), username, password)) {
            String createDbSql = "CREATE DATABASE IF NOT EXISTS `" + database + "` " +
                    "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            try (PreparedStatement ps = conn.prepareStatement(createDbSql)) {
                ps.executeUpdate();
            }
            logger.info("Verified database exists or created: " + database);
        }
    }

    /**
     * Ensure required tables and views exist inside the configured database.
     */
    private void ensureSchema() {
        String createChatMessages = """
            CREATE TABLE IF NOT EXISTS `chat_messages` (
                `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                `message_uuid` VARCHAR(36) UNIQUE NOT NULL COMMENT 'Unique identifier for the message',
                `server_name` VARCHAR(255) NOT NULL COMMENT 'Name of the server instance',
                `world_name` VARCHAR(255) NOT NULL COMMENT 'World where the message was sent',
                `player_uuid` VARCHAR(36) NOT NULL COMMENT 'UUID of the player who sent the message',
                `player_name` VARCHAR(16) NOT NULL COMMENT 'Display name of the player at time of message',
                `message_content` TEXT NOT NULL COMMENT 'The actual chat message content',
                `message_type` ENUM('CHAT', 'PRIVATE', 'BROADCAST', 'COMMAND', 'SYSTEM', 'JOIN', 'LEAVE', 'DEATH', 'ACHIEVEMENT') NOT NULL DEFAULT 'CHAT' COMMENT 'Type of message',
                `channel` VARCHAR(255) DEFAULT 'global' COMMENT 'Chat channel (global, local, team, etc.)',
                `location_x` DOUBLE NULL COMMENT 'X coordinate where message was sent',
                `location_y` DOUBLE NULL COMMENT 'Y coordinate where message was sent',
                `location_z` DOUBLE NULL COMMENT 'Z coordinate where message was sent',
                `recipient_uuid` VARCHAR(36) NULL COMMENT 'UUID of recipient for private messages',
                `recipient_name` VARCHAR(16) NULL COMMENT 'Name of recipient for private messages',
                `is_cancelled` BOOLEAN DEFAULT FALSE COMMENT 'Whether the message was cancelled by a plugin',
                `metadata_json` JSON NULL COMMENT 'Additional metadata in JSON format',
                `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When the message was sent',
                INDEX `idx_player_uuid` (`player_uuid`),
                INDEX `idx_player_name` (`player_name`),
                INDEX `idx_timestamp` (`timestamp`),
                INDEX `idx_message_type` (`message_type`),
                INDEX `idx_world_name` (`world_name`),
                INDEX `idx_channel` (`channel`),
                INDEX `idx_server_name` (`server_name`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        String createPlayerSessions = """
            CREATE TABLE IF NOT EXISTS `player_sessions` (
                `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                `session_uuid` VARCHAR(36) UNIQUE NOT NULL COMMENT 'Unique session identifier',
                `player_uuid` VARCHAR(36) NOT NULL COMMENT 'UUID of the player',
                `player_name` VARCHAR(16) NOT NULL COMMENT 'Player name at session start',
                `server_name` VARCHAR(255) NOT NULL COMMENT 'Server instance name',
                `login_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When player joined',
                `logout_time` TIMESTAMP NULL COMMENT 'When player left (NULL if still online)',
                `ip_address` VARCHAR(45) NULL COMMENT 'Player IP address (IPv4/IPv6)',
                `client_brand` VARCHAR(255) NULL COMMENT 'Minecraft client brand/version',
                INDEX `idx_player_uuid` (`player_uuid`),
                INDEX `idx_login_time` (`login_time`),
                INDEX `idx_server_name` (`server_name`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        String createServerEvents = """
            CREATE TABLE IF NOT EXISTS `server_events` (
                `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                `event_uuid` VARCHAR(36) UNIQUE NOT NULL COMMENT 'Unique event identifier',
                `server_name` VARCHAR(255) NOT NULL COMMENT 'Server instance name',
                `event_type` ENUM('SERVER_START', 'SERVER_STOP', 'SERVER_RESTART', 'PLUGIN_LOAD', 'PLUGIN_UNLOAD', 'WORLD_LOAD', 'WORLD_UNLOAD', 'BACKUP', 'ERROR', 'WARNING', 'INFO') NOT NULL COMMENT 'Type of server event',
                `event_message` TEXT NOT NULL COMMENT 'Description of the event',
                `severity_level` ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') DEFAULT 'MEDIUM' COMMENT 'Event severity',
                `metadata_json` JSON NULL COMMENT 'Additional event data in JSON format',
                `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When the event occurred',
                INDEX `idx_event_type` (`event_type`),
                INDEX `idx_timestamp` (`timestamp`),
                INDEX `idx_server_name` (`server_name`),
                INDEX `idx_severity_level` (`severity_level`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        String createCommandLogs = """
            CREATE TABLE IF NOT EXISTS `command_logs` (
                `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                `command_uuid` VARCHAR(36) UNIQUE NOT NULL COMMENT 'Unique identifier for the command log',
                `server_name` VARCHAR(255) NOT NULL COMMENT 'Server instance name',
                `source_type` ENUM('PLAYER','CONSOLE','RCON','COMMAND_BLOCK','OTHER') NOT NULL COMMENT 'From where the command originated',
                `player_uuid` VARCHAR(36) NULL COMMENT 'UUID if source is PLAYER',
                `player_name` VARCHAR(16) NULL COMMENT 'Name if source is PLAYER',
                `command_text` TEXT NOT NULL COMMENT 'The raw command text',
                `world_name` VARCHAR(255) NULL COMMENT 'World (if applicable, e.g., player or command block)',
                `location_x` DOUBLE NULL COMMENT 'X coordinate if applicable',
                `location_y` DOUBLE NULL COMMENT 'Y coordinate if applicable',
                `location_z` DOUBLE NULL COMMENT 'Z coordinate if applicable',
                `is_cancelled` BOOLEAN DEFAULT FALSE COMMENT 'Whether the command was cancelled',
                `metadata_json` JSON NULL COMMENT 'Additional metadata in JSON format',
                `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When the command was executed',
                INDEX `idx_source_type` (`source_type`),
                INDEX `idx_player_uuid` (`player_uuid`),
                INDEX `idx_timestamp_cmd` (`timestamp`),
                INDEX `idx_server_name_cmd` (`server_name`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        String createView = """
            CREATE OR REPLACE VIEW `v_chat_messages_full` AS
            SELECT 
                cm.id,
                cm.message_uuid,
                cm.server_name,
                cm.world_name,
                cm.player_uuid,
                cm.player_name,
                cm.message_content,
                cm.message_type,
                cm.channel,
                cm.location_x,
                cm.location_y,
                cm.location_z,
                cm.recipient_uuid,
                cm.recipient_name,
                cm.is_cancelled,
                cm.metadata_json,
                cm.`timestamp`,
                ps.ip_address,
                ps.client_brand
            FROM chat_messages cm
            LEFT JOIN player_sessions ps ON cm.player_uuid = ps.player_uuid 
                AND cm.`timestamp` BETWEEN ps.login_time AND COALESCE(ps.logout_time, NOW())
            """;

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(createChatMessages)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(createPlayerSessions)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(createServerEvents)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(createCommandLogs)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(createView)) {
                ps.executeUpdate();
            } catch (SQLException viewEx) {
                // View creation is not critical for basic functionality; log warning but continue
                logger.log(Level.WARNING, "Failed to create or replace view v_chat_messages_full", viewEx);
            }
            logger.info("Database schema verified (tables/views ensured).");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to ensure database schema", e);
        }
    }
    
    /**
     * Get a database connection from the pool
     */
    public Connection getConnection() throws SQLException {
        if (!isEnabled || dataSource == null) {
            throw new SQLException("DatabaseManager is not initialized");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Execute a query asynchronously
     */
    public CompletableFuture<Void> executeAsync(String sql, Object... parameters) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                
                // Set parameters
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
                
                statement.executeUpdate();
                
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to execute async query: " + sql, e);
            }
        }, asyncExecutor);
    }
    
    /**
     * Execute a batch of statements asynchronously
     */
    public CompletableFuture<Void> executeBatchAsync(String sql, Object[]... parameterSets) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                
                for (Object[] parameters : parameterSets) {
                    for (int i = 0; i < parameters.length; i++) {
                        statement.setObject(i + 1, parameters[i]);
                    }
                    statement.addBatch();
                }
                
                statement.executeBatch();
                
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to execute batch query: " + sql, e);
            }
        }, asyncExecutor);
    }
    
    /**
     * Check if the database manager is enabled and ready
     */
    public boolean isEnabled() {
        return isEnabled && dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * Get connection pool statistics for monitoring
     */
    public String getPoolStats() {
        if (dataSource == null) {
            return "DataSource not initialized";
        }
        
        return String.format("Pool Stats - Active: %d, Idle: %d, Total: %d, Waiting: %d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
    }
    
    /**
     * Shutdown the database manager and close all connections
     */
    public void shutdown() {
        logger.info("Shutting down DatabaseManager...");
        
        if (asyncExecutor != null && !asyncExecutor.isShutdown()) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
        
        isEnabled = false;
    }
    
    /**
     * Reload database configuration and reinitialize connection
     */
    public boolean reload() {
        logger.info("Reloading DatabaseManager...");
        shutdown();
        return initialize();
    }
}
