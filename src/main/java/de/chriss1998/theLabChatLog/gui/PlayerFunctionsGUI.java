package de.chriss1998.theLabChatLog.gui;

import de.chriss1998.theLabChatLog.TheLabChatLog;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class PlayerFunctionsGUI {

    private static final int SIZE = 54;

    public static final int SLOT_HISTORY_ALL = 20;
    public static final int SLOT_HISTORY_CHAT = 22;
    public static final int SLOT_HISTORY_CMD = 24;
    public static final int SLOT_BACK = 45;
    public static final int SLOT_CLOSE = 49;

    private static final String TITLE = ChatColor.DARK_AQUA + "ChatLog | Funktionen";

    public static final NamespacedKey KEY_ACTION = new NamespacedKey(TheLabChatLog.getPlugin(TheLabChatLog.class), "action");

    public static final String ACTION_HISTORY_ALL = "history_all";
    public static final String ACTION_HISTORY_CHAT = "history_chat";
    public static final String ACTION_HISTORY_CMD = "history_cmd";
    public static final String ACTION_BACK = "back";
    public static final String ACTION_CLOSE = "close";

    private PlayerFunctionsGUI() {}

    public static void open(Player viewer, UUID targetUuid, String targetName, int backPage) {
        if (viewer == null || targetUuid == null) return;
        Holder holder = new Holder(targetUuid, targetName, backPage);
        String namePart = targetName != null ? (ChatColor.GOLD + targetName) : (ChatColor.GOLD + targetUuid.toString());
        Inventory inv = Bukkit.createInventory(holder, SIZE, TITLE + ChatColor.GRAY + " • " + namePart);
        holder.setInventory(inv);

        // Center row items
        inv.setItem(SLOT_HISTORY_ALL, makeActionItem(Material.BOOK, ChatColor.AQUA + "Gesamte Historie", ACTION_HISTORY_ALL, targetUuid, targetName,
                ChatColor.GRAY + "Zeigt Chat + Commands (Seite 1)"));
        inv.setItem(SLOT_HISTORY_CHAT, makeActionItem(Material.WRITABLE_BOOK, ChatColor.AQUA + "Chat-Historie", ACTION_HISTORY_CHAT, targetUuid, targetName,
                ChatColor.GRAY + "Zeigt nur Chat-Nachrichten (Seite 1)"));
        inv.setItem(SLOT_HISTORY_CMD, makeActionItem(Material.COMPARATOR, ChatColor.AQUA + "Command-Historie", ACTION_HISTORY_CMD, targetUuid, targetName,
                ChatColor.GRAY + "Zeigt nur Commands (Seite 1)"));

        // Nav bar
        fillNavBar(inv);
        inv.setItem(SLOT_BACK, makeActionItem(Material.ARROW, ChatColor.YELLOW + "Zurück", ACTION_BACK, targetUuid, targetName));
        inv.setItem(SLOT_CLOSE, makeActionItem(Material.BARRIER, ChatColor.RED + "Schließen", ACTION_CLOSE, targetUuid, targetName));

        viewer.openInventory(inv);
    }

    private static void fillNavBar(Inventory inv) {
        for (int i = SLOT_BACK; i <= SLOT_BACK + 8; i++) {
            if (inv.getItem(i) != null) continue;
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta m = pane.getItemMeta();
            assert m != null;
            m.setDisplayName(" ");
            pane.setItemMeta(m);
            inv.setItem(i, pane);
        }
    }

    private static ItemStack makeActionItem(Material type, String displayName, String action, UUID targetUuid, String targetName, String... loreLines) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        meta.setDisplayName(displayName);
        if (loreLines != null && loreLines.length > 0) {
            List<String> lore = new ArrayList<>(Arrays.asList(loreLines));
            meta.setLore(lore);
        }
        // Verstecke zusätzliche Tooltips (inkl. etwaiger Warnungen) bei allen GUI-Items
        try {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        } catch (Throwable ignored) {
            // Fallback: ignorieren, falls Flag in älteren APIs nicht verfügbar ist
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(PlayerSelectGUI.KEY_UUID, PersistentDataType.STRING, targetUuid.toString());
        if (targetName != null) {
            pdc.set(PlayerSelectGUI.KEY_NAME, PersistentDataType.STRING, targetName);
        }
        pdc.set(KEY_ACTION, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    public static String readAction(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(KEY_ACTION, PersistentDataType.STRING);
    }

    public static class Holder implements InventoryHolder {
        private final UUID targetUuid;
        private final String targetName;
        private final int backPage;
        private Inventory inventory;

        public Holder(UUID targetUuid, String targetName, int backPage) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.backPage = backPage;
        }

        public UUID getTargetUuid() { return targetUuid; }
        public String getTargetName() { return targetName; }
        public int getBackPage() { return backPage; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        @Override
        public @org.jetbrains.annotations.NotNull Inventory getInventory() {
            // Gib eine nicht-null Inventory-Referenz zurück; falls noch nicht gesetzt, kleinen Dummy zurückgeben
            return (inventory != null) ? inventory : Bukkit.createInventory(null, 9);
        }
    }
}
