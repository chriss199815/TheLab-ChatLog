package de.chriss1998.theLabChatLog.command;

import de.chriss1998.theLabChatLog.TheLabChatLog;
import de.chriss1998.theLabChatLog.dao.ChatDAO;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class ChatLogCommand implements CommandExecutor {

    private final TheLabChatLog plugin;

    public ChatLogCommand(TheLabChatLog plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("thelab.chatlog.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Berechtigung.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " <reload|stats|test|countme>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadAllConfigs();
                sender.sendMessage(ChatColor.GREEN + "ChatLog-Konfiguration neu geladen.");
                return true;

            case "stats":
                String pool = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getPoolStats() : "DB nicht initialisiert";
                sender.sendMessage(ChatColor.AQUA + "ChatLog Status:");
                sender.sendMessage(ChatColor.GRAY + " - DB: " + (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled()));
                sender.sendMessage(ChatColor.GRAY + " - Pool: " + pool);
                return true;

            case "test":
                // Schreibe einen kurzen Testeintrag, um DB-Schreibzugriff zu prüfen
                try {
                    plugin.getDatabaseManager().executeAsync("INSERT INTO server_events(event_uuid, server_name, event_type, event_message, severity_level) VALUES (UUID(), ?, 'INFO', 'ChatLog TEST', 'LOW')", plugin.getConfig().getString("server.name", "Unknown"));
                    sender.sendMessage(ChatColor.GREEN + "Test-Event in DB geschrieben (server_events). Prüfe Datenbank.");
                } catch (Exception ex) {
                    sender.sendMessage(ChatColor.RED + "Fehler beim Schreiben in die DB: " + ex.getMessage());
                }
                return true;

            case "countme":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Nur Ingame verfügbar.");
                    return true;
                }
                Player p = (Player) sender;
                ChatDAO dao = plugin.getChatDAO();
                if (dao == null) {
                    sender.sendMessage(ChatColor.RED + "ChatDAO nicht verfügbar.");
                    return true;
                }
                CompletableFuture<Integer> fut = dao.getMessageCountByPlayerAsync(p.getUniqueId().toString());
                fut.thenAccept(count -> sender.sendMessage(ChatColor.GOLD + "Du hast " + count + " geloggte Nachrichten."));
                return true;

            default:
                sender.sendMessage(ChatColor.YELLOW + "/" + label + " <reload|stats|test|countme>");
                return true;
        }
    }
}
