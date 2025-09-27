-- Chat Log Database Schema
-- Compatible with MySQL 8.x and MariaDB 11.x

CREATE DATABASE IF NOT EXISTS `thelab_chatlog` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `thelab_chatlog`;

-- Table for storing all chat messages
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Stores all chat messages and related events';

-- Table for storing player sessions
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tracks player login/logout sessions';

-- Table for server events and system messages
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Logs server events and system messages';

-- Create a view for easy chat message querying with player info
CREATE VIEW `v_chat_messages_full` AS
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
    cm.timestamp,
    ps.ip_address,
    ps.client_brand
FROM chat_messages cm
LEFT JOIN player_sessions ps ON cm.player_uuid = ps.player_uuid 
    AND cm.timestamp BETWEEN ps.login_time AND COALESCE(ps.logout_time, NOW());
