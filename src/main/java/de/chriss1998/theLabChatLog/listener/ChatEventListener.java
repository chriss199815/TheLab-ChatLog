package de.chriss1998.theLabChatLog.listener;

import de.chriss1998.theLabChatLog.TheLabChatLog;
import de.chriss1998.theLabChatLog.dao.ChatDAO;
import de.chriss1998.theLabChatLog.model.ChatMessage;
import de.chriss1998.theLabChatLog.model.CommandLog;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens to various Minecraft events and logs chat messages to the database
 */
public class ChatEventListener implements Listener {
    
    private final TheLabChatLog plugin;
    private final ChatDAO chatDAO;
    private final Logger logger;
    private final String serverName;
    
    // Configuration flags
    private boolean chatLoggingEnabled;
    private boolean logChat;
    private boolean logPrivateMessages;
    private boolean logCommands;
    private boolean logSystemMessages;
    private boolean logJoinLeave;
    private boolean logDeathMessages;
    private boolean logAchievements;
    private boolean logBroadcasts;
    private boolean filterSensitiveData;
    private int maxMessageLength;
    private List<String> enabledChannels;
    private List<String> excludedPlayers;
    
    public ChatEventListener(TheLabChatLog plugin, ChatDAO chatDAO) {
        this.plugin = plugin;
        this.chatDAO = chatDAO;
        this.logger = plugin.getLogger();
        this.serverName = plugin.getConfig().getString("server.name", "Unknown");
        
        loadConfiguration();
    }
    
    /**
     * Load configuration settings
     */
    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        
        this.chatLoggingEnabled = config.getBoolean("chat_logging.enabled", true);
        this.logChat = config.getBoolean("chat_logging.log_types.chat", true);
        this.logPrivateMessages = config.getBoolean("chat_logging.log_types.private_messages", true);
        this.logCommands = config.getBoolean("chat_logging.log_types.commands", false);
        this.logSystemMessages = config.getBoolean("chat_logging.log_types.system_messages", true);
        this.logJoinLeave = config.getBoolean("chat_logging.log_types.join_leave", true);
        this.logDeathMessages = config.getBoolean("chat_logging.log_types.death_messages", true);
        this.logAchievements = config.getBoolean("chat_logging.log_types.achievements", true);
        this.logBroadcasts = config.getBoolean("chat_logging.log_types.broadcasts", true);
        
        this.filterSensitiveData = config.getBoolean("chat_logging.filter_sensitive_data", true);
        this.maxMessageLength = config.getInt("chat_logging.max_message_length", 1000);
        this.enabledChannels = config.getStringList("chat_logging.channels");
        this.excludedPlayers = config.getStringList("chat_logging.excluded_players");
        
        if (config.getBoolean("debug.enabled", false)) {
            logger.info("Chat logging configuration loaded - Enabled: " + chatLoggingEnabled
                    + ", logCommands=" + logCommands);
        } else {
            logger.info("Chat logging configuration loaded - Enabled: " + chatLoggingEnabled);
        }
    }
    
    /**
     * Handle regular chat messages
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!chatLoggingEnabled || !logChat) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check if player is excluded
        if (isPlayerExcluded(player)) {
            return;
        }
        
        // Check if channel is enabled (if channel filtering is configured)
        String channel = determineChannel(event);
        if (!enabledChannels.isEmpty() && !enabledChannels.contains(channel)) {
            return;
        }
        
        String messageContent = processMessageContent(event.getMessage());
        
        ChatMessage chatMessage = new ChatMessage.Builder(
            serverName,
            player.getUniqueId().toString(),
            player.getName(),
            messageContent
        )
        .world(player.getWorld().getName())
        .type(ChatMessage.MessageType.CHAT)
        .channel(channel)
        .location(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())
        .cancelled(event.isCancelled())
        .build();
        
        // Log the message asynchronously
        chatDAO.insertChatMessageAsync(chatMessage).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to log chat message from " + player.getName(), throwable);
            return null;
        });
        
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            logger.info("Logged chat message from " + player.getName() + ": " + 
                       (messageContent.length() > 50 ? messageContent.substring(0, 50) + "..." : messageContent));
        }
    }
    
    /**
     * Handle player commands (if enabled)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!chatLoggingEnabled) {
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                logger.info("Skipping player command logging: chatLoggingEnabled=" + chatLoggingEnabled);
            }
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check if player is excluded
        if (isPlayerExcluded(player)) {
            return;
        }
        
        String command = event.getMessage();
        
        // Filter out sensitive commands if configured
        if (filterSensitiveData && isSensitiveCommand(command)) {
            command = filterSensitiveCommand(command);
        }
        
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            logger.info("Logging command from " + player.getName() + ": " + command);
        }

        CommandLog log = new CommandLog.Builder()
            .server(serverName)
            .source(CommandLog.SourceType.PLAYER)
            .player(player.getUniqueId().toString(), player.getName())
            .command(command)
            .world(player.getWorld().getName())
            .location(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())
            .cancelled(event.isCancelled())
            .build();

        chatDAO.insertCommandLogAsync(log).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to log player command from " + player.getName(), throwable);
            return null;
        });
    }
    
    /**
     * Handle server/console and command block commands
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (!chatLoggingEnabled) {
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                logger.info("Skipping server command logging: chatLoggingEnabled=" + chatLoggingEnabled);
            }
            return;
        }

        String cmd = event.getCommand();
        if (filterSensitiveData && isSensitiveCommand(cmd)) {
            cmd = filterSensitiveCommand(cmd);
        }

        // Avoid double logging for remote console; RemoteServerCommandEvent will handle it
        if (event.getSender() instanceof RemoteConsoleCommandSender) {
            return;
        }

        CommandLog.Builder builder = new CommandLog.Builder()
            .server(serverName)
            .command(cmd)
            .cancelled(false);

        if (event.getSender() instanceof ConsoleCommandSender) {
            builder.source(CommandLog.SourceType.CONSOLE);
        } else if (event.getSender() instanceof BlockCommandSender bcs) {
            builder.source(CommandLog.SourceType.COMMAND_BLOCK);
            var loc = bcs.getBlock().getLocation();
            String worldName = loc.getWorld() != null ? loc.getWorld().getName() : null;
            builder.world(worldName).location(loc.getX(), loc.getY(), loc.getZ());
        } else {
            builder.source(CommandLog.SourceType.OTHER);
        }

        CommandLog log = builder.build();

        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            logger.info("Logging server command: " + cmd + " (source=" + log.getSourceType() + ")");
        }

        chatDAO.insertCommandLogAsync(log).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to log server command", throwable);
            return null;
        });
    }

    /**
     * Handle remote console (RCON) commands
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemoteServerCommand(RemoteServerCommandEvent event) {
        if (!chatLoggingEnabled) {
            return;
        }

        String cmd = event.getCommand();
        if (filterSensitiveData && isSensitiveCommand(cmd)) {
            cmd = filterSensitiveCommand(cmd);
        }

        CommandLog log = new CommandLog.Builder()
            .server(serverName)
            .source(CommandLog.SourceType.RCON)
            .command(cmd)
            .cancelled(false)
            .build();

        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            logger.info("Logging RCON command: " + cmd);
        }

        chatDAO.insertCommandLogAsync(log).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to log RCON command", throwable);
            return null;
        });
    }

    /**
     * Handle player join events
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!chatLoggingEnabled || !logJoinLeave) {
            return;
        }
        
        Player player = event.getPlayer();
        String joinMessage = event.getJoinMessage();
        
        if (joinMessage == null || joinMessage.isEmpty()) {
            joinMessage = player.getName() + " joined the game";
        }
        
        ChatMessage chatMessage = new ChatMessage.Builder(
            serverName,
            player.getUniqueId().toString(),
            player.getName(),
            joinMessage
        )
        .world(player.getWorld().getName())
        .type(ChatMessage.MessageType.JOIN)
        .channel("system")
        .location(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())
        .build();
        
        chatDAO.insertChatMessageAsync(chatMessage).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to log join event for " + player.getName(), throwable);
            return null;
        });
    }
    
    /**
     * Handle player quit events
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!chatLoggingEnabled || !logJoinLeave) {
            return;
        }
        
        Player player = event.getPlayer();
        String quitMessage = event.getQuitMessage();
        
        if (quitMessage == null || quitMessage.isEmpty()) {
            quitMessage = player.getName() + " left the game";
        }
        
        ChatMessage chatMessage = new ChatMessage.Builder(
            serverName,
            player.getUniqueId().toString(),
            player.getName(),
            quitMessage
        )
        .world(player.getWorld().getName())
        .type(ChatMessage.MessageType.LEAVE)
        .channel("system")
        .location(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())
        .build();
        
        chatDAO.insertChatMessageAsync(chatMessage).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to log quit event for " + player.getName(), throwable);
            return null;
        });
    }
    
    /**
     * Handle player death events
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!chatLoggingEnabled || !logDeathMessages) {
            return;
        }
        
        Player player = event.getEntity();
        String deathMessage = event.getDeathMessage();
        
        if (deathMessage == null || deathMessage.isEmpty()) {
            deathMessage = player.getName() + " died";
        }
        
        ChatMessage chatMessage = new ChatMessage.Builder(
            serverName,
            player.getUniqueId().toString(),
            player.getName(),
            deathMessage
        )
        .world(player.getWorld().getName())
        .type(ChatMessage.MessageType.DEATH)
        .channel("system")
        .location(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())
        .build();
        
        chatDAO.insertChatMessageAsync(chatMessage).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to log death event for " + player.getName(), throwable);
            return null;
        });
    }
    
    /**
     * Handle player achievement events
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        if (!chatLoggingEnabled || !logAchievements) {
            return;
        }
        
        Player player = event.getPlayer();
        String advancementKey = event.getAdvancement().getKey().getKey();
        
        // Only log visible advancements (achievements)
        AdvancementDisplay display = event.getAdvancement().getDisplay();
        if (display == null || !display.shouldAnnounceChat()) {
            return;
        }
        
        String achievementMessage = player.getName() + " has made the advancement [" + 
                                   display.getTitle() + "]";
        
        ChatMessage chatMessage = new ChatMessage.Builder(
            serverName,
            player.getUniqueId().toString(),
            player.getName(),
            achievementMessage
        )
        .world(player.getWorld().getName())
        .type(ChatMessage.MessageType.ACHIEVEMENT)
        .channel("system")
        .location(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())
        .metadata("{\"advancement_key\":\"" + advancementKey + "\"}")
        .build();
        
        chatDAO.insertChatMessageAsync(chatMessage).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to log achievement for " + player.getName(), throwable);
            return null;
        });
    }
    
    /**
     * Check if a player is excluded from logging
     */
    private boolean isPlayerExcluded(Player player) {
        String playerUuid = player.getUniqueId().toString();
        String playerName = player.getName();
        
        return excludedPlayers.contains(playerUuid) || excludedPlayers.contains(playerName);
    }
    
    /**
     * Determine the channel for a chat message
     */
    private String determineChannel(AsyncPlayerChatEvent event) {
        // This is a simplified implementation
        // You might want to integrate with chat plugins like EssentialsX, TownyChat, etc.
        return "global";
    }
    
    /**
     * Process message content (truncate, filter, etc.)
     */
    private String processMessageContent(String message) {
        if (message == null) {
            return "";
        }
        
        // Truncate if too long
        if (message.length() > maxMessageLength) {
            message = message.substring(0, maxMessageLength - 3) + "...";
        }
        
        // Additional filtering can be added here
        return message;
    }
    
    /**
     * Check if a command contains sensitive information
     */
    private boolean isSensitiveCommand(String command) {
        String lowerCommand = command.toLowerCase();
        return lowerCommand.contains("password") || 
               lowerCommand.contains("token") || 
               lowerCommand.contains("/login") ||
               lowerCommand.contains("/register") ||
               lowerCommand.startsWith("/op ") ||
               lowerCommand.startsWith("/deop ");
    }
    
    /**
     * Filter sensitive information from commands
     */
    private String filterSensitiveCommand(String command) {
        String[] parts = command.split(" ");
        if (parts.length > 1) {
            // Replace arguments with [FILTERED]
            return parts[0] + " [FILTERED]";
        }
        return command;
    }
    
    /**
     * Reload configuration
     */
    public void reloadConfiguration() {
        loadConfiguration();
        logger.info("ChatEventListener configuration reloaded");
    }
}
