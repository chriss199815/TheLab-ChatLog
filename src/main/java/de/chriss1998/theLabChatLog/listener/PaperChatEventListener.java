package de.chriss1998.theLabChatLog.listener;

import de.chriss1998.theLabChatLog.TheLabChatLog;
import de.chriss1998.theLabChatLog.dao.ChatDAO;
import de.chriss1998.theLabChatLog.model.ChatMessage;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Paper-specific listener using AsyncChatEvent (Adventure API)
 */
public class PaperChatEventListener implements Listener {

    private final TheLabChatLog plugin;
    private final ChatDAO chatDAO;
    private final Logger logger;
    private final String serverName;

    // Configuration flags
    private boolean chatLoggingEnabled;
    private boolean logChat;
    private boolean filterSensitiveData;
    private int maxMessageLength;
    private List<String> enabledChannels;
    private List<String> excludedPlayers;

    public PaperChatEventListener(TheLabChatLog plugin, ChatDAO chatDAO) {
        this.plugin = plugin;
        this.chatDAO = chatDAO;
        this.logger = plugin.getLogger();
        this.serverName = plugin.getConfig().getString("server.name", "Unknown");
        loadConfiguration();
    }

    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        this.chatLoggingEnabled = config.getBoolean("chat_logging.enabled", true);
        this.logChat = config.getBoolean("chat_logging.log_types.chat", true);
        this.filterSensitiveData = config.getBoolean("chat_logging.filter_sensitive_data", true);
        this.maxMessageLength = config.getInt("chat_logging.max_message_length", 1000);
        this.enabledChannels = config.getStringList("chat_logging.channels");
        this.excludedPlayers = config.getStringList("chat_logging.excluded_players");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!chatLoggingEnabled || !logChat) {
            return;
        }

        Player player = event.getPlayer();
        if (isPlayerExcluded(player)) {
            return;
        }

        // Determine channel (Paper Adventure doesn't expose channels by default; use global)
        String channel = "global";
        if (!enabledChannels.isEmpty() && !enabledChannels.contains(channel)) {
            return;
        }

        // Convert Component to plain text
        String messageContent = PlainTextComponentSerializer.plainText().serialize(event.message());
        messageContent = processMessageContent(messageContent);

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

        chatDAO.insertChatMessageAsync(chatMessage).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to log chat message (Paper) from " + player.getName(), throwable);
            return null;
        });

        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            logger.info("[Paper] Logged chat message from " + player.getName() + ": " +
                    (messageContent.length() > 50 ? messageContent.substring(0, 50) + "..." : messageContent));
        }
    }

    private boolean isPlayerExcluded(Player player) {
        String playerUuid = player.getUniqueId().toString();
        String playerName = player.getName();
        return excludedPlayers.contains(playerUuid) || excludedPlayers.contains(playerName);
    }

    private String processMessageContent(String message) {
        if (message == null) return "";
        if (message.length() > maxMessageLength) {
            message = message.substring(0, maxMessageLength - 3) + "...";
        }
        return message;
    }

    public void reloadConfiguration() { loadConfiguration(); }
}
