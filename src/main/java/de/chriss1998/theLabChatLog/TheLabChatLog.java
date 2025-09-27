package de.chriss1998.theLabChatLog;

import org.bukkit.plugin.java.JavaPlugin;
import de.chriss1998.theLabChatLog.database.DatabaseManager;
import de.chriss1998.theLabChatLog.dao.ChatDAO;
import de.chriss1998.theLabChatLog.listener.ChatEventListener;
import de.chriss1998.theLabChatLog.command.ChatLogCommand;
import org.bukkit.event.Listener;
import de.chriss1998.theLabChatLog.listener.PaperChatEventBridge;

public final class TheLabChatLog extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ChatDAO chatDAO;
    private ChatEventListener bukkitChatListener;
    private Listener paperChatListener;

    @Override
    public void onEnable() {
        // Stelle sicher, dass der Plugin-Datenordner existiert
        if (!getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            getDataFolder().mkdirs();
        }

        // Standard-Konfiguration aus resources nach plugins/<PluginName>/config.yml kopieren (falls nicht vorhanden)
        saveDefaultConfig();

        // Datenbank initialisieren
        databaseManager = new DatabaseManager(this);
        boolean dbReady = databaseManager.initialize();

        if (!dbReady) {
            getLogger().severe("Konnte die Datenbank nicht initialisieren. Chat-Logging wird vorerst deaktiviert.");
        } else {
            // DAO initialisieren und Listener registrieren
            chatDAO = new ChatDAO(databaseManager, getLogger());
            // Immer Bukkit-Listener für Join/Leave/Death/Achievement/Commands
            bukkitChatListener = new ChatEventListener(this, chatDAO);
            getServer().getPluginManager().registerEvents(bukkitChatListener, this);

            // Auf Paper zusätzlich AsyncChatEvent via reflektionsbasierter Bridge registrieren
            if (isClassPresent("io.papermc.paper.event.player.AsyncChatEvent")) {
                boolean registered = PaperChatEventBridge.tryRegister(this, chatDAO);
                if (!registered) {
                    getLogger().warning("Paper AsyncChatEvent vorhanden, aber Bridge konnte nicht registriert werden.");
                }
            } else {
                getLogger().info("Paper AsyncChatEvent nicht gefunden – verwende Bukkit AsyncPlayerChatEvent.");
            }
        }

        getLogger().info("TheLab-ChatLog erfolgreich aktiviert.");

        // Command-Executor registrieren
        if (getCommand("chatlog") != null) {
            getCommand("chatlog").setExecutor(new ChatLogCommand(this));
        }
    }

    @Override
    public void onDisable() {
        // Ressourcen freigeben
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("TheLab-ChatLog deaktiviert.");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ChatDAO getChatDAO() {
        return chatDAO;
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // --- Konfigurations-Reload für Listener ---
    public void reloadAllConfigs() {
        // Eigene Config neu laden
        reloadConfig();
        // Listener informieren
        if (bukkitChatListener != null) {
            bukkitChatListener.reloadConfiguration();
        }
        if (paperChatListener != null) {
            try {
                paperChatListener.getClass().getMethod("reloadConfiguration").invoke(paperChatListener);
            } catch (Throwable ignored) {
                // Methode existiert evtl. nicht – ignorieren
            }
        }
        getLogger().info("Konfiguration neu geladen.");
    }
}
