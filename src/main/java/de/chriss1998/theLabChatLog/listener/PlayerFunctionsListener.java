package de.chriss1998.theLabChatLog.listener;

import de.chriss1998.theLabChatLog.TheLabChatLog;
import de.chriss1998.theLabChatLog.dao.ChatDAO;
import de.chriss1998.theLabChatLog.model.ChatMessage;
import de.chriss1998.theLabChatLog.model.CommandLog;
import de.chriss1998.theLabChatLog.gui.PlayerFunctionsGUI;
import de.chriss1998.theLabChatLog.gui.PlayerSelectGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class PlayerFunctionsListener implements Listener {

    private static final int PREVIEW_MAX = 120;

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof PlayerFunctionsGUI.Holder h)) return;

        event.setCancelled(true);
        if (event.getRawSlot() >= top.getSize()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player viewer = (Player) event.getWhoClicked();
        if (!viewer.hasPermission("thelab.chatlog.admin")) {
            viewer.sendMessage(ChatColor.RED + "Keine Berechtigung.");
            viewer.closeInventory();
            return;
        }

        UUID targetUuid = h.getTargetUuid();
        String targetName = h.getTargetName();

        String action = PlayerFunctionsGUI.readAction(clicked);
        if (action == null) return;

        TheLabChatLog plugin = TheLabChatLog.getPlugin(TheLabChatLog.class);
        ChatDAO dao = plugin.getChatDAO();
        if (dao == null) {
            viewer.sendMessage(ChatColor.RED + "ChatDAO nicht verfügbar.");
            return;
        }

        switch (action) {
            case PlayerFunctionsGUI.ACTION_CLOSE:
                viewer.closeInventory();
                return;
            case PlayerFunctionsGUI.ACTION_BACK:
                // Zurück zur Spielerauswahl
                PlayerSelectGUI.open(viewer, Math.max(1, h.getBackPage()));
                return;
            case PlayerFunctionsGUI.ACTION_HISTORY_ALL: {
                int page = 1, limit = 10, offset = 0;
                dao.getCombinedHistoryByPlayerAsync(targetUuid.toString(), limit, offset).thenAccept(entries -> Bukkit.getScheduler().runTask(plugin, () -> {
                    viewer.sendMessage(ChatColor.AQUA + "Historie (ALLE) für " + ChatColor.GOLD + safeName(targetName, targetUuid) + ChatColor.AQUA + " – Seite " + page);
                    if (entries.isEmpty()) {
                        viewer.sendMessage(ChatColor.GRAY + "Keine Einträge gefunden.");
                        return;
                    }
                    for (ChatDAO.HistoryEntry e : entries) {
                        String ts = e.timestamp != null ? e.timestamp.toLocalDateTime().toString() : "";
                        String loc = formatLoc(e.worldName, e.locationX, e.locationY, e.locationZ);
                        String text = trim(e.text);
                        if ("CHAT".equalsIgnoreCase(e.entryType)) {
                            viewer.sendMessage(ChatColor.DARK_AQUA + "[" + ts + "] " + ChatColor.YELLOW + "[CHAT] " + ChatColor.GRAY + "(" + loc + ") " + ChatColor.WHITE + text);
                        } else {
                            viewer.sendMessage(ChatColor.DARK_AQUA + "[" + ts + "] " + ChatColor.LIGHT_PURPLE + "[CMD]  " + ChatColor.GRAY + "(" + loc + ") " + ChatColor.WHITE + text);
                        }
                    }
                    viewer.sendMessage(ChatColor.DARK_GRAY + "— Ende Seite " + page + " —");
                })).exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(ChatColor.RED + "Fehler beim Laden der Historie: " + ex.getMessage()));
                    return null;
                });
                return;
            }
            case PlayerFunctionsGUI.ACTION_HISTORY_CHAT: {
                int page = 1, limit = 10, offset = 0;
                dao.getChatMessagesByPlayerAsync(targetUuid.toString(), limit, offset).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
                    viewer.sendMessage(ChatColor.AQUA + "Historie (CHAT) für " + ChatColor.GOLD + safeName(targetName, targetUuid) + ChatColor.AQUA + " – Seite " + page);
                    if (list.isEmpty()) {
                        viewer.sendMessage(ChatColor.GRAY + "Keine Chat-Nachrichten gefunden.");
                        return;
                    }
                    for (ChatMessage m : list) {
                        String ts = m.getTimestamp() != null ? m.getTimestamp().toString() : "";
                        String loc = formatLoc(m.getWorldName(), m.getLocationX(), m.getLocationY(), m.getLocationZ());
                        String text = trim(m.getMessageContent());
                        viewer.sendMessage(ChatColor.DARK_AQUA + "[" + ts + "] " + ChatColor.YELLOW + "[" + m.getMessageType() + "] " + ChatColor.GRAY + "(" + loc + ") " + ChatColor.WHITE + text);
                    }
                    viewer.sendMessage(ChatColor.DARK_GRAY + "— Ende Seite " + page + " —");
                })).exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(ChatColor.RED + "Fehler beim Laden der Chat-Historie: " + ex.getMessage()));
                    return null;
                });
                return;
            }
            case PlayerFunctionsGUI.ACTION_HISTORY_CMD: {
                int page = 1, limit = 10, offset = 0;
                dao.getCommandLogsByPlayerAsync(targetUuid.toString(), limit, offset).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
                    viewer.sendMessage(ChatColor.AQUA + "Historie (COMMANDS) für " + ChatColor.GOLD + safeName(targetName, targetUuid) + ChatColor.AQUA + " – Seite " + page);
                    if (list.isEmpty()) {
                        viewer.sendMessage(ChatColor.GRAY + "Keine Befehls-Einträge gefunden.");
                        return;
                    }
                    for (CommandLog m : list) {
                        String ts = m.getTimestamp() != null ? m.getTimestamp().toString() : "";
                        String loc = formatLoc(m.getWorldName(), m.getLocationX(), m.getLocationY(), m.getLocationZ());
                        String text = trim(m.getCommandText());
                        viewer.sendMessage(ChatColor.DARK_AQUA + "[" + ts + "] " + ChatColor.LIGHT_PURPLE + "[" + m.getSourceType() + "] " + ChatColor.GRAY + "(" + loc + ") " + ChatColor.WHITE + text);
                    }
                    viewer.sendMessage(ChatColor.DARK_GRAY + "— Ende Seite " + page + " —");
                })).exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(ChatColor.RED + "Fehler beim Laden der Befehls-Historie: " + ex.getMessage()));
                    return null;
                });
                return;
            }
            default:
                // Unbekannte Aktion ignorieren
        }
    }

    private String safeName(String name, UUID uuid) {
        if (name != null && !name.isEmpty()) return name;
        return uuid != null ? uuid.toString() : "?";
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
