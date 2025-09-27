package de.chriss1998.theLabChatLog.listener;

import de.chriss1998.theLabChatLog.gui.PlayerSelectGUI;
import de.chriss1998.theLabChatLog.gui.PlayerFunctionsGUI;
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

public class PlayerSelectListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory viewTop = event.getView().getTopInventory();
        InventoryHolder holder = viewTop != null ? viewTop.getHolder() : null;
        if (!(holder instanceof PlayerSelectGUI.Holder)) return;

        // Blockiere alle Interaktionen in dieser GUI
        event.setCancelled(true);

        // Nur Klicks im oberen Fensterteil beachten
        if (event.getRawSlot() >= viewTop.getSize()) return;

        ItemStack clicked = event.getCurrentItem();
        Player viewer = (Player) event.getWhoClicked();
        PlayerSelectGUI.Holder h = (PlayerSelectGUI.Holder) holder;

        int slot = event.getRawSlot();
        if (slot == PlayerSelectGUI.SLOT_CLOSE) {
            viewer.closeInventory();
            return;
        }
        if (slot == PlayerSelectGUI.SLOT_PREV) {
            int prev = Math.max(1, h.getPage() - 1);
            if (prev != h.getPage()) {
                PlayerSelectGUI.open(viewer, prev);
            }
            return;
        }
        if (slot == PlayerSelectGUI.SLOT_NEXT) {
            // maxPage neu anhand akt. Online-Spieler berechnen (kann sich ändern)
            int size = Bukkit.getOnlinePlayers().size();
            int maxPage = Math.max(1, (int) Math.ceil(size / 45.0));
            int next = Math.min(maxPage, h.getPage() + 1);
            if (next != h.getPage()) {
                PlayerSelectGUI.open(viewer, next);
            }
            return;
        }

        if (clicked == null || clicked.getType() == Material.AIR) return;

        UUID targetId = PlayerSelectGUI.readUUID(clicked);
        if (targetId == null) return;

        String name = PlayerSelectGUI.readName(clicked);
        if (name == null) {
            Player target = Bukkit.getPlayer(targetId);
            name = target != null ? target.getName() : targetId.toString();
        }

        // Öffne die Funktions-GUI für den ausgewählten Spieler
        PlayerFunctionsGUI.open(viewer, targetId, name, h.getPage());
    }
}
