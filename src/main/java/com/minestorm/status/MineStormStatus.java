package com.minestorm.status;

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

        this.messages = new Messages(this);
        this.statusManager = new StatusManager(this);

        getServer().getPluginManager().registerEvents(new ActivityListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        PluginCommand command = getCommand("status");
        if (command == null) {
            getLogger().severe("Command 'status' is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        StatusCommand statusCommand = new StatusCommand(this);
        command.setExecutor(statusCommand);
        command.setTabCompleter(statusCommand);

        // Players already online (e.g. after /reload) start with fresh activity.
        for (Player player : Bukkit.getOnlinePlayers()) {
            statusManager.markActive(player.getUniqueId());
        }

        FileConfiguration config = getConfig();

        if (config.getBoolean("auto-afk.enabled", true)) {
            long interval = Math.max(1L, config.getLong("auto-afk.check-interval-ticks", 20L));
            new AutoAfkTask(this).runTaskTimer(this, interval, interval);
        }

        if (config.getBoolean("action-bar.enabled", true)) {
            long interval = Math.max(1L, config.getLong("action-bar.update-interval-ticks", 20L));
            new ActionBarTask(this).runTaskTimer(this, 1L, interval);
        }

        getLogger().info("MineStormStatus " + getPluginMeta().getVersion() + " enabled.");
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

    public Messages getMessages() {
        return messages;
    }

    public StatusManager getStatusManager() {
        return statusManager;
    }
}
