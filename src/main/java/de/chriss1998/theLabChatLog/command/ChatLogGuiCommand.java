package de.chriss1998.theLabChatLog.command;

import de.chriss1998.theLabChatLog.TheLabChatLog;
import de.chriss1998.theLabChatLog.gui.PlayerSelectGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatLogGuiCommand implements CommandExecutor {

    private final TheLabChatLog plugin;

    public ChatLogGuiCommand(TheLabChatLog plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Nur Ingame verfügbar.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("thelab.chatlog.admin")) {
            player.sendMessage(ChatColor.RED + "Keine Berechtigung.");
            return true;
        }

        // Öffne die Spieler-Auswahl-GUI (Seite 1)
        PlayerSelectGUI.open(player, 1);
        return true;
    }
}
