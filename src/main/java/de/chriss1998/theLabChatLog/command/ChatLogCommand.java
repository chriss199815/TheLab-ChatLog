package de.chriss1998.theLabChatLog.command;

import de.chriss1998.theLabChatLog.TheLabChatLog;
import de.chriss1998.theLabChatLog.dao.ChatDAO;
import de.chriss1998.theLabChatLog.model.ChatMessage;
import de.chriss1998.theLabChatLog.model.CommandLog;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChatLogCommand implements CommandExecutor {

    private final TheLabChatLog plugin;
    private static final int PREVIEW_MAX = 120;
    private static final Map<String, String> SUBCOMMANDS = buildSubcommandHelp();

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
            printHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
            case "h":
            case "?":
                printHelp(sender, label);
                return true;
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

            case "history":
                handleHistoryAll(sender, label, args);
                return true;

            case "historychat":
                handleHistoryChat(sender, label, args);
                return true;

            case "historycmd":
                handleHistoryCmd(sender, label, args);
                return true;

            default:
                suggestSubcommand(sender, label, args[0]);
                sender.sendMessage(ChatColor.GRAY + "Nutze " + ChatColor.YELLOW + "/" + label + " help" + ChatColor.GRAY + " für eine Übersicht.");
                return true;
        }
    }

    private void handleHistoryAll(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Benutzung: /" + label + " history <Spieler> [Seite]");
            sender.sendMessage(ChatColor.GRAY + "Beispiel: " + ChatColor.YELLOW + "/" + label + " history Steve 1");
            sender.sendMessage(ChatColor.DARK_GRAY + "Tipp: /" + label + " help");
            return;
        }
        String targetName = args[1];
        int page = parsePage(args, 2);
        int limit = 10;
        int offset = (page - 1) * limit;

        String uuid = resolveUuidByName(targetName);
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden: " + targetName);
            return;
        }

        ChatDAO dao = plugin.getChatDAO();
        if (dao == null) {
            sender.sendMessage(ChatColor.RED + "ChatDAO nicht verfügbar.");
            return;
        }

        dao.getCombinedHistoryByPlayerAsync(uuid, limit, offset).thenAccept(entries -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.AQUA + "Historie (ALLE) für " + ChatColor.GOLD + targetName + ChatColor.AQUA + " – Seite " + page);
                if (entries.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Keine Einträge gefunden.");
                    return;
                }
                for (ChatDAO.HistoryEntry e : entries) {
                    String ts = e.timestamp != null ? e.timestamp.toLocalDateTime().toString() : "";
                    String loc = formatLoc(e.worldName, e.locationX, e.locationY, e.locationZ);
                    String text = trim(e.text);
                    if ("CHAT".equalsIgnoreCase(e.entryType)) {
                        sender.sendMessage(ChatColor.DARK_AQUA + "[" + ts + "] " + ChatColor.YELLOW + "[CHAT] " + ChatColor.GRAY + "(" + loc + ") " + ChatColor.WHITE + text);
                    } else {
                        sender.sendMessage(ChatColor.DARK_AQUA + "[" + ts + "] " + ChatColor.LIGHT_PURPLE + "[CMD]  " + ChatColor.GRAY + "(" + loc + ") " + ChatColor.WHITE + text);
                    }
                }
                sender.sendMessage(ChatColor.DARK_GRAY + "— Ende Seite " + page + " —");
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ChatColor.RED + "Fehler beim Laden der Historie: " + ex.getMessage()));
            return null;
        });
    }

    private void handleHistoryChat(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Benutzung: /" + label + " historychat <Spieler> [Seite]");
            sender.sendMessage(ChatColor.GRAY + "Beispiel: " + ChatColor.YELLOW + "/" + label + " historychat Steve 2");
            sender.sendMessage(ChatColor.DARK_GRAY + "Tipp: /" + label + " help");
            return;
        }
        String targetName = args[1];
        int page = parsePage(args, 2);
        int limit = 10;
        int offset = (page - 1) * limit;

        String uuid = resolveUuidByName(targetName);
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden: " + targetName);
            return;
        }

        ChatDAO dao = plugin.getChatDAO();
        if (dao == null) {
            sender.sendMessage(ChatColor.RED + "ChatDAO nicht verfügbar.");
            return;
        }

        dao.getChatMessagesByPlayerAsync(uuid, limit, offset).thenAccept(list -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.AQUA + "Historie (CHAT) für " + ChatColor.GOLD + targetName + ChatColor.AQUA + " – Seite " + page);
                if (list.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Keine Chat-Nachrichten gefunden.");
                    return;
                }
                for (ChatMessage m : list) {
                    String ts = m.getTimestamp() != null ? m.getTimestamp().toString() : "";
                    String loc = formatLoc(m.getWorldName(), m.getLocationX(), m.getLocationY(), m.getLocationZ());
                    String text = trim(m.getMessageContent());
                    sender.sendMessage(ChatColor.DARK_AQUA + "[" + ts + "] " + ChatColor.YELLOW + "[" + m.getMessageType() + "] " + ChatColor.GRAY + "(" + loc + ") " + ChatColor.WHITE + text);
                }
                sender.sendMessage(ChatColor.DARK_GRAY + "— Ende Seite " + page + " —");
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ChatColor.RED + "Fehler beim Laden der Chat-Historie: " + ex.getMessage()));
            return null;
        });
    }

    private void handleHistoryCmd(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Benutzung: /" + label + " historycmd <Spieler> [Seite]");
            sender.sendMessage(ChatColor.GRAY + "Beispiel: " + ChatColor.YELLOW + "/" + label + " historycmd Steve 3");
            sender.sendMessage(ChatColor.DARK_GRAY + "Tipp: /" + label + " help");
            return;
        }
        String targetName = args[1];
        int page = parsePage(args, 2);
        int limit = 10;
        int offset = (page - 1) * limit;

        String uuid = resolveUuidByName(targetName);
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden: " + targetName);
            return;
        }

        ChatDAO dao = plugin.getChatDAO();
        if (dao == null) {
            sender.sendMessage(ChatColor.RED + "ChatDAO nicht verfügbar.");
            return;
        }

        dao.getCommandLogsByPlayerAsync(uuid, limit, offset).thenAccept(list -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.AQUA + "Historie (COMMANDS) für " + ChatColor.GOLD + targetName + ChatColor.AQUA + " – Seite " + page);
                if (list.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Keine Befehls-Einträge gefunden.");
                    return;
                }
                for (CommandLog m : list) {
                    String ts = m.getTimestamp() != null ? m.getTimestamp().toString() : "";
                    String loc = formatLoc(m.getWorldName(), m.getLocationX(), m.getLocationY(), m.getLocationZ());
                    String text = trim(m.getCommandText());
                    sender.sendMessage(ChatColor.DARK_AQUA + "[" + ts + "] " + ChatColor.LIGHT_PURPLE + "[" + m.getSourceType() + "] " + ChatColor.GRAY + "(" + loc + ") " + ChatColor.WHITE + text);
                }
                sender.sendMessage(ChatColor.DARK_GRAY + "— Ende Seite " + page + " —");
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ChatColor.RED + "Fehler beim Laden der Befehls-Historie: " + ex.getMessage()));
            return null;
        });
    }

    private void printHelp(CommandSender sender, String label) {
        String version = plugin.getDescription().getVersion();
        sender.sendMessage(ChatColor.AQUA + "==== ChatLog Hilfe " + (version.isEmpty() ? "" : ("v" + version + " ")) + "====");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " <Unterbefehl> [Argumente]");
        sender.sendMessage(ChatColor.GRAY + "Verfügbar:");
        for (Map.Entry<String, String> e : SUBCOMMANDS.entrySet()) {
            String desc = String.format(e.getValue(), label);
            sender.sendMessage(ChatColor.GOLD + " - /" + label + " " + e.getKey() + ChatColor.GRAY + " — " + desc);
        }
        sender.sendMessage(ChatColor.GRAY + "Beispiele:");
        sender.sendMessage(ChatColor.DARK_AQUA + " • /" + label + " history " + sender.getName() + " 1");
        sender.sendMessage(ChatColor.DARK_AQUA + " • /" + label + " historychat "  + sender.getName() +  "2");
        sender.sendMessage(ChatColor.DARK_AQUA + " • /" + label + " historycmd " + sender.getName() + " 3");
        sender.sendMessage(ChatColor.DARK_GRAY + "============================");
    }

    private void suggestSubcommand(CommandSender sender, String label, String input) {
        if (input == null || input.isEmpty()) return;
        String lower = input.toLowerCase();
        String best = null;
        int bestScore = Integer.MAX_VALUE;
        for (String key : SUBCOMMANDS.keySet()) {
            int d = levenshteinDistance(lower, key.toLowerCase());
            if (lower.startsWith(key.substring(0, 1))) {
                d = Math.max(0, d - 1); // leichte Bevorzugung gleicher Anfangsbuchstaben
            }
            if (d < bestScore) {
                bestScore = d;
                best = key;
            }
        }
        if (best != null && bestScore <= 3) {
            sender.sendMessage(ChatColor.GRAY + "Meintest du: " + ChatColor.YELLOW + "/" + label + " " + best + ChatColor.GRAY + "?");
        }
    }

    private static Map<String, String> buildSubcommandHelp() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("help", "Zeigt diese Hilfe.");
        m.put("reload", "Lädt die Konfiguration neu.");
        m.put("stats", "Zeigt DB-Status und Pool-Infos.");
        m.put("test", "Schreibt einen Testeintrag in die DB.");
        m.put("countme", "Zeigt deine Anzahl geloggter Nachrichten (nur Spieler).");
        m.put("history", "Zeigt Chat- und Command-Historie eines Spielers. Nutzung: /%s history <Spieler> [Seite]");
        m.put("historychat", "Zeigt nur Chat-Historie eines Spielers. Nutzung: /%s historychat <Spieler> [Seite]");
        m.put("historycmd", "Zeigt nur Command-Historie eines Spielers. Nutzung: /%s historycmd <Spieler> [Seite]");
        return Collections.unmodifiableMap(m);
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    private int parsePage(String[] args, int index) {
        if (args.length <= index) return 1;
        try {
            int p = Integer.parseInt(args[index]);
            return Math.max(1, p);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private String resolveUuidByName(String name) {
        // 1) Online players (fast path)
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return online.getUniqueId().toString();
        
        // 2) Spigot: search known offline players by exact name (case-insensitive)
        try {
            for (OfflinePlayer off : plugin.getServer().getOfflinePlayers()) {
                if (off != null && off.getName() != null && off.getName().equalsIgnoreCase(name)) {
                    UUID id = off.getUniqueId();
                    if (id != null) return id.toString();
                }
            }
        } catch (Throwable ignored) { }

        // 3) Intentionally avoid Bukkit#getOfflinePlayer(String) because it is deprecated and may perform
        //    blocking lookups and unreliable name-based resolution. If nothing was found above, return null.

        return null;
    }

    private String formatLoc(String world, Double x, Double y, Double z) {
        String w = world != null ? world : "?";
        if (x == null || y == null || z == null) return w;
        return String.format("%s @ %.1f/%.1f/%.1f", w, x, y, z);
    }

    private String trim(String s) {
        if (s == null) return "";
        if (s.length() <= PREVIEW_MAX) return s;
        return s.substring(0, Math.max(0, PREVIEW_MAX - 3)) + "...";
    }
}
