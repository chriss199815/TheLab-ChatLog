package de.chriss1998.theLabChatLog.listener;

import de.chriss1998.theLabChatLog.TheLabChatLog;
import de.chriss1998.theLabChatLog.dao.ChatDAO;
import de.chriss1998.theLabChatLog.model.ChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

/**
 * Reflection-based bridge to listen to Paper's AsyncChatEvent without compile-time dependency.
 */
public class PaperChatEventBridge implements Listener {

    private final TheLabChatLog plugin;
    private final ChatDAO chatDAO;

    // Config
    private boolean chatLoggingEnabled;
    private boolean logChat;
    private boolean filterSensitiveData;
    private int maxMessageLength;
    private List<String> enabledChannels;
    private List<String> excludedPlayers;

    private PaperChatEventBridge(TheLabChatLog plugin, ChatDAO chatDAO) {
        this.plugin = plugin;
        this.chatDAO = chatDAO;
        loadConfiguration();
    }

    @SuppressWarnings("unchecked")
    public static boolean tryRegister(TheLabChatLog plugin, ChatDAO chatDAO) {
        Class<? extends Event> asyncChatEventClass;
        try {
            Class<? extends Event> tmp = (Class<? extends Event>) Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            asyncChatEventClass = tmp;
        } catch (ClassNotFoundException e) {
            return false; // Not a Paper server
        }

        PaperChatEventBridge bridge = new PaperChatEventBridge(plugin, chatDAO);

        EventExecutor executor = (listener, event) -> bridge.handleAsyncChat(event);

        plugin.getServer().getPluginManager().registerEvent(
                asyncChatEventClass,
                bridge,
                EventPriority.MONITOR,
                executor,
                plugin,
                false
        );
        plugin.getLogger().info("Paper AsyncChatEvent-Bridge registriert (reflective)");
        return true;
    }

    public void reloadConfiguration() { loadConfiguration(); }

    private void loadConfiguration() {
        var config = plugin.getConfig();
        this.chatLoggingEnabled = config.getBoolean("chat_logging.enabled", true);
        this.logChat = config.getBoolean("chat_logging.log_types.chat", true);
        this.filterSensitiveData = config.getBoolean("chat_logging.filter_sensitive_data", true);
        this.maxMessageLength = config.getInt("chat_logging.max_message_length", 1000);
        this.enabledChannels = config.getStringList("chat_logging.channels");
        this.excludedPlayers = config.getStringList("chat_logging.excluded_players");
    }

    private void handleAsyncChat(Object event) {
        if (!chatLoggingEnabled || !logChat) {
            return;
        }
        try {
            // Player via getPlayer()
            Method getPlayer = event.getClass().getMethod("getPlayer");
            Player player = (Player) getPlayer.invoke(event);
            if (isPlayerExcluded(player)) return;

            // Channel (global default)
            String channel = "global";
            if (!enabledChannels.isEmpty() && !enabledChannels.contains(channel)) return;

            // Cancelled?
            boolean isCancelled = false;
            try {
                Method isCancelledM = event.getClass().getMethod("isCancelled");
                isCancelled = (boolean) isCancelledM.invoke(event);
            } catch (NoSuchMethodException ignored) { }

            // message() -> Component; serialize via PlainTextComponentSerializer
            Method messageMethod = event.getClass().getMethod("message");
            Object component = messageMethod.invoke(event);
            String messageContent = serializeComponentToPlain(component);
            messageContent = processMessageContent(messageContent);

            ChatMessage chatMessage = new ChatMessage.Builder(
                    plugin.getConfig().getString("server.name", "Unknown"),
                    player.getUniqueId().toString(),
                    player.getName(),
                    messageContent
            )
            .world(player.getWorld().getName())
            .type(ChatMessage.MessageType.CHAT)
            .channel(channel)
            .location(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())
            .cancelled(isCancelled)
            .build();

            chatDAO.insertChatMessageAsync(chatMessage).exceptionally(throwable -> {
                plugin.getLogger().log(Level.WARNING, "Failed to log chat message (Paper bridge) from " + player.getName(), throwable);
                return null;
            });
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            plugin.getLogger().log(Level.WARNING, "Reflection error handling AsyncChatEvent", e);
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
            message = message.substring(0, Math.max(0, maxMessageLength - 3)) + "...";
        }
        return message;
    }

    private String serializeComponentToPlain(Object component) {
        try {
            Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Method plainTextMethod = serializerClass.getMethod("plainText");
            Object serializer = plainTextMethod.invoke(null);
            Method serialize = serializerClass.getMethod("serialize", Class.forName("net.kyori.adventure.text.Component"));
            return (String) serialize.invoke(serializer, component);
        } catch (Throwable t) {
            // Fallback
            return String.valueOf(component);
        }
    }
}
