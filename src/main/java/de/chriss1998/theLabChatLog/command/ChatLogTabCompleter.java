package de.chriss1998.theLabChatLog.command;

import de.chriss1998.theLabChatLog.TheLabChatLog;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ChatLogTabCompleter implements TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "help", "reload", "stats", "test", "countme",
            "history", "historychat", "historycmd"
    );

    private final TheLabChatLog plugin;

    public ChatLogTabCompleter(TheLabChatLog plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("thelab.chatlog.admin")) {
            return Collections.emptyList();
        }
        if (!"chatlog".equalsIgnoreCase(command.getName())) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream()
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            // Spieler-Vorschläge für history/historychat/historycmd
            if (sub.equals("history") || sub.equals("historychat") || sub.equals("historycmd")) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                        .map(p -> p.getName())
                        .filter(name -> name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted()
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        if (args.length == 3) {
            if (sub.equals("history") || sub.equals("historychat") || sub.equals("historycmd")) {
                // Seitenvorschläge 1..5
                List<String> pages = new ArrayList<>();
                for (int i = 1; i <= 5; i++) pages.add(Integer.toString(i));
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return pages.stream()
                        .filter(p -> p.startsWith(prefix))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }
}
