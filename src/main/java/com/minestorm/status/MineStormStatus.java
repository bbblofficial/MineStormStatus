package com.minestorm.status;

import com.minestorm.status.command.MineStormStatusCommand;
import com.minestorm.status.command.StatusCommand;
import com.minestorm.status.listener.ActivityListener;
import com.minestorm.status.listener.ChatListener;
import com.minestorm.status.manager.StatusManager;
import com.minestorm.status.task.ActionBarTask;
import com.minestorm.status.task.AutoAfkTask;
import com.minestorm.status.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MineStormStatus extends JavaPlugin {

    private Messages messages;
    private StatusManager statusManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false); // only written if it does not exist yet
        reloadConfig();

        this.messages = new Messages(this);
        this.statusManager = new StatusManager(this);

        getServer().getPluginManager().registerEvents(new ActivityListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        if (!registerCommands()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Players already online (e.g. after /reload) start with fresh activity.
        for (Player player : Bukkit.getOnlinePlayers()) {
            statusManager.markActive(player.getUniqueId());
        }

        startTasks();
        getLogger().info("MineStormStatus v" + getVersion() + " enabled. Created by Muvixo.");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (statusManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (statusManager.getStatus(player.getUniqueId()) != null) {
                    player.sendActionBar(Component.empty());
                }
            }
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

    @SuppressWarnings("deprecation")
    public String getVersion() {
        return getDescription().getVersion();
    }

    public Messages getMessages() {
        return messages;
    }

    public StatusManager getStatusManager() {
        return statusManager;
    }
}
