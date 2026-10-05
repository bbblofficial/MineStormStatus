package com.minestorm.status;

import com.minestorm.status.command.MineStormStatusCommand;
import com.minestorm.status.command.StatusCommand;
import com.minestorm.status.listener.ActivityListener;
import com.minestorm.status.listener.ChatListener;
import com.minestorm.status.manager.StatusManager;
import com.minestorm.status.net.ProxyBridge;
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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

public final class MineStormStatus extends JavaPlugin {

    /** Bump when config.yml / messages.yml change in an incompatible way. */
    private static final int FILE_VERSION = 2;

    private Messages messages;
    private StatusManager statusManager;
    private ActionBar actionBar;
    private ProxyBridge proxyBridge;
    private BukkitTask afkTask;
    private BukkitTask hudTask;

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

        this.proxyBridge = new ProxyBridge(this);
        if (getConfig().getBoolean("network.proxy", true)) {
            proxyBridge.enable();
        }

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
        getLogger().info("MineStormStatus v" + getVersion() + " enabled."
                + (proxyBridge.isEnabled() ? " Cross-server relay active." : "")
                + " Created by Muvixo.");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (proxyBridge != null) proxyBridge.disable();
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

    private void prepareFile(String name) {
        File file = new File(getDataFolder(), name);
        if (file.exists()) {
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            if (current.getInt("config-version", 0) < FILE_VERSION) {
                File backup = new File(getDataFolder(), name + ".old");
                if (backup.exists()) backup.delete();
                if (file.renameTo(backup)) {
                    getLogger().warning(name + " was outdated. Renamed to " + backup.getName()
                            + " and a fresh " + name + " was created.");
                }
            }
        }
        if (!file.exists()) saveResource(name, false);
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

    /**
     * Starts the AFK and HUD tasks. Only OUR two tasks are cancelled here - the old
     * Bukkit.getScheduler().cancelTasks(this) would also kill the relay heartbeat task.
     */
    private void startTasks() {
        cancelOwnTasks();
        FileConfiguration config = getConfig();

        if (config.getBoolean("auto-afk.enabled", true)) {
            long interval = Math.max(1L, config.getLong("auto-afk.check-interval-ticks", 20L));
            afkTask = new AutoAfkTask(this).runTaskTimer(this, interval, interval);
        }
        if (config.getBoolean("action-bar.enabled", true)) {
            long interval = Math.max(1L, config.getLong("action-bar.update-interval-ticks", 20L));
            hudTask = new ActionBarTask(this).runTaskTimer(this, 1L, interval);
        }
    }

    private void cancelOwnTasks() {
        if (afkTask != null) { afkTask.cancel(); afkTask = null; }
        if (hudTask != null) { hudTask.cancel(); hudTask = null; }
    }

    public void reloadPlugin() {
        reloadConfig();
        messages.reload();
        startTasks();
        // network.proxy / network.secret / network.server-name used to need a full restart.
        if (proxyBridge != null) {
            proxyBridge.disable();
            if (getConfig().getBoolean("network.proxy", true)) proxyBridge.enable();
        }
    }

    public String getVersion() { return getDescription().getVersion(); }
    public Messages getMessages() { return messages; }
    public StatusManager getStatusManager() { return statusManager; }
    public ActionBar getActionBar() { return actionBar; }
    public ProxyBridge getProxyBridge() { return proxyBridge; }
}
