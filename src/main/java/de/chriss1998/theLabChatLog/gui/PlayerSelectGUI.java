package de.chriss1998.theLabChatLog.gui;

import de.chriss1998.theLabChatLog.TheLabChatLog;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PlayerSelectGUI {

    private static final int GUI_SIZE = 54; // 6 Reihen
    private static final int PAGE_SIZE = 45; // 5 Reihen für Inhalte
    public static final int SLOT_PREV = 45;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_NEXT = 53;

    private static final String TITLE = ChatColor.DARK_AQUA + "ChatLog | Spieler wählen";

    public static final NamespacedKey KEY_UUID = new NamespacedKey(TheLabChatLog.getPlugin(TheLabChatLog.class), "uuid");
    public static final NamespacedKey KEY_NAME = new NamespacedKey(TheLabChatLog.getPlugin(TheLabChatLog.class), "name");

    private PlayerSelectGUI() {}

    public static void open(Player viewer, int page) {
        if (viewer == null) return;
        TheLabChatLog plugin = TheLabChatLog.getPlugin(TheLabChatLog.class);
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(p -> p.getName().toLowerCase()));

        int maxPage = Math.max(1, (int) Math.ceil(players.size() / (double) PAGE_SIZE));
        int current = Math.max(1, Math.min(page, maxPage));

        Inventory inv = buildInventory(plugin, players, current, maxPage);
        viewer.openInventory(inv);
    }

    private static Inventory buildInventory(TheLabChatLog plugin, List<Player> players, int page, int maxPage) {
        String title = TITLE + ChatColor.GRAY + " • Seite " + ChatColor.GOLD + page + ChatColor.GRAY + "/" + ChatColor.GOLD + maxPage;
        Holder holder = new Holder(page, maxPage);
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, players.size());

        // Inhalte: Spielerköpfe
        int slot = 0;
        for (int i = start; i < end; i++) {
            Player p = players.get(i);
            inv.setItem(slot++, makePlayerHead(plugin, p));
        }

        // Falls leer, Info-Item
        if (players.isEmpty()) {
            ItemStack info = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = info.getItemMeta();
            assert meta != null;
            meta.setDisplayName(ChatColor.GRAY + "Keine Spieler online");
            info.setItemMeta(meta);
            inv.setItem(22, info);
        }

        // Navigationszeile füllen
        fillNavBar(inv);

        if (page > 1) {
            inv.setItem(SLOT_PREV, makeNav(Material.ARROW, ChatColor.YELLOW + "Zurück"));
        }
        inv.setItem(SLOT_CLOSE, makeNav(Material.BARRIER, ChatColor.RED + "Schließen"));
        if (page < maxPage) {
            inv.setItem(SLOT_NEXT, makeNav(Material.ARROW, ChatColor.YELLOW + "Weiter"));
        }

        return inv;
    }

    private static void fillNavBar(Inventory inv) {
        for (int i = SLOT_PREV; i <= SLOT_NEXT; i++) {
            if (inv.getItem(i) != null) continue;
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta m = pane.getItemMeta();
            assert m != null;
            m.setDisplayName(" ");
            pane.setItemMeta(m);
            inv.setItem(i, pane);
        }
    }

    private static ItemStack makeNav(Material type, String name) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makePlayerHead(TheLabChatLog plugin, Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta baseMeta = head.getItemMeta();
        if (baseMeta instanceof SkullMeta) {
            SkullMeta meta = (SkullMeta) baseMeta;
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.GOLD + target.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Klicke, um auszuwählen");
            meta.setLore(lore);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_UUID, PersistentDataType.STRING, target.getUniqueId().toString());
            pdc.set(KEY_NAME, PersistentDataType.STRING, target.getName());
            head.setItemMeta(meta);
        }
        return head;
    }

    public static UUID readUUID(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String s = pdc.get(KEY_UUID, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static String readName(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String n = pdc.get(KEY_NAME, PersistentDataType.STRING);
        if (n != null) return n;
        // Fallback: DisplayName ohne Farben
        String dn = meta.getDisplayName();
        if (dn != null && !dn.isEmpty()) {
            return ChatColor.stripColor(dn);
        }
        return null;
    }

    public static class Holder implements InventoryHolder {
        private final int page;
        private final int maxPage;
        private Inventory inventory;

        public Holder(int page, int maxPage) {
            this.page = page;
            this.maxPage = maxPage;
        }

        public int getPage() { return page; }
        public int getMaxPage() { return maxPage; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        @Override
        public @org.jetbrains.annotations.NotNull Inventory getInventory() {
            return (inventory != null) ? inventory : Bukkit.createInventory(null, 9);
        }
    }
}
