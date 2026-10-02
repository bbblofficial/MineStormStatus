package com.minestorm.status;

import com.minestorm.status.command.MineStormStatusCommand;
import com.minestorm.status.command.StatusCommand;
import com.minestorm.status.listener.ActivityListener;
import com.minestorm.status.listener.ChatListener;
import com.minestorm.status.manager.StatusManager;
import com.minestorm.status.task.ActionBarTask;
import com.minestorm.status.task.AutoAfkTask;
import com.minestorm.status.util.ActionBar;
import com.minestorm.status.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MineStormStatus extends JavaPlugin {

    /** Bump when config.yml / messages.yml change in an incompatible way. */
    private static final int FILE_VERSION = 2;

    private Messages messages;
    private StatusManager statusManager;
    private ActionBar actionBar;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        prepareFile("config.yml");
        prepareFile("messages.yml");
        reloadConfig();

        this.messages = new Messages(this);
        this.actionBar = new ActionBar(this);
        this.statusManager = new StatusManager(this);
        this.statusManager.load();

        getServer().getPluginManager().registerEvents(new ActivityListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        if (!registerCommands()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Players already online (e.g. after /reload).
        for (Player player : Bukkit.getOnlinePlayers()) {
            statusManager.markActive(player.getUniqueId());
            statusManager.restoreSaved(player.getUniqueId());
        }

        startTasks();
        getLogger().info("MineStormStatus v" + getVersion() + " enabled. Created by Muvixo.");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (statusManager != null) {
            if (actionBar != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (statusManager.getStatus(player.getUniqueId()) != null) {
                        actionBar.clear(player);
                    }
                }
            }
            statusManager.shutdown();
        }
    }

    /**
     * Makes sure the file exists. If an old/incompatible version is found it is renamed to
     * "&lt;name&gt;.old" and a fresh default is written.
     */
    private void prepareFile(String name) {
        File file = new File(getDataFolder(), name);
        if (file.exists()) {
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            if (current.getInt("config-version", 0) < FILE_VERSION) {
                File backup = new File(getDataFolder(), name + ".old");
                if (backup.exists()) {
                    backup.delete();
                }
                if (file.renameTo(backup)) {
                    getLogger().warning(name + " was outdated. It was renamed to " + backup.getName()
                            + " and a new " + name + " was created.");
                }
            }
        }
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    private boolean registerCommands() {
        PluginCommand status = getCommand("status");
        PluginCommand main = getCommand("minestormstatus");
        if (status == null || main == null) {
            getLogger().severe("Commands are missing from plugin.yml. Disabling plugin.");
            return false;
        }
        StatusCommand statusCommand = new StatusCommand(this);
        status.setExecutor(statusCommand);
        status.setTabCompleter(statusCommand);

        MineStormStatusCommand mainCommand = new MineStormStatusCommand(this);
        main.setExecutor(mainCommand);
        main.setTabCompleter(mainCommand);
        return true;
    }

    private void startTasks() {
        Bukkit.getScheduler().cancelTasks(this);
        FileConfiguration config = getConfig();

        if (config.getBoolean("auto-afk.enabled", true)) {
            long interval = Math.max(1L, config.getLong("auto-afk.check-interval-ticks", 20L));
            new AutoAfkTask(this).runTaskTimer(this, interval, interval);
        }

        if (config.getBoolean("action-bar.enabled", true)) {
            long interval = Math.max(1L, config.getLong("action-bar.update-interval-ticks", 20L));
            new ActionBarTask(this).runTaskTimer(this, 1L, interval);
        }
    }

    /** Reloads config.yml and messages.yml and restarts the repeating tasks. */
    public void reloadPlugin() {
        reloadConfig();
        messages.reload();
        startTasks();
    }

    public String getVersion() {
        return getDescription().getVersion();
    }

    public Messages getMessages() {
        return messages;
    }

    public StatusManager getStatusManager() {
        return statusManager;
    }

    public ActionBar getActionBar() {
        return actionBar;
    }
}
