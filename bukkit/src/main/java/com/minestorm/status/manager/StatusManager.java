package com.minestorm.status.manager;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.net.ProxyBridge;
import com.minestorm.status.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe storage for player statuses and activity timestamps.
 * Chat events are fired asynchronously on some server versions.
 */
public final class StatusManager {

    private final MineStormStatus plugin;
    private final Map<UUID, StatusType> statuses = new ConcurrentHashMap<UUID, StatusType>();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<UUID, Long>();
    private final Set<UUID> autoIdle = ConcurrentHashMap.newKeySet();
    private final Map<UUID, StatusType> saved = new ConcurrentHashMap<UUID, StatusType>();
    private final Object fileLock = new Object();

    public StatusManager(MineStormStatus plugin) { this.plugin = plugin; }

    /** Tell the other backends about a status change (no-op when the relay is off). */
    private void announce(String playerName, String statusKey) {
        ProxyBridge bridge = plugin.getProxyBridge();
        if (bridge != null && bridge.isEnabled() && playerName != null) {
            bridge.broadcastStatus(playerName, statusKey);
        }
    }

    // ------------------------------------------------------- statuses

    public StatusType getStatus(UUID id) { return statuses.get(id); }

    public void setStatus(UUID id, StatusType type) {
        autoIdle.remove(id);
        statuses.put(id, type);
    }

    public void setAutoIdle(UUID id) {
        autoIdle.add(id);
        statuses.put(id, StatusType.IDLE);
    }

    /** Auto-Idle that is also announced to the other servers. */
    public void setAutoIdle(Player player) {
        setAutoIdle(player.getUniqueId());
        announce(player.getName(), StatusType.IDLE.getKey());
    }

    public void clearStatus(UUID id) {
        autoIdle.remove(id);
        statuses.remove(id);
    }

    public void applyAndNotify(Player player, StatusType type) {
        setStatus(player.getUniqueId(), type);
        Messages messages = plugin.getMessages();
        messages.send(player, "status.set." + type.getKey());
        if (plugin.getConfig().getBoolean("action-bar.enabled", true)) {
            plugin.getActionBar().send(player, messages.hud(player, type));
        }
        announce(player.getName(), type.getKey());
    }

    public void clearAndNotify(Player player) {
        UUID id = player.getUniqueId();
        if (getStatus(id) == null) {
            plugin.getMessages().send(player, "status.nothing-to-clear");
            return;
        }
        clearStatus(id);
        plugin.getMessages().send(player, "status.cleared");
        if (plugin.getConfig().getBoolean("action-bar.enabled", true)) {
            plugin.getActionBar().clear(player);
        }
        announce(player.getName(), "clear");
    }

    public Map<UUID, StatusType> snapshot() {
        return new HashMap<UUID, StatusType>(statuses);
    }

    // ------------------------------------------------------- activity

    /** @return how long the player was idle if an auto-Idle was removed, else -1. */
    public long markActive(UUID id) {
        long now = System.currentTimeMillis();
        Long previous = lastActivity.put(id, now);
        if (!autoIdle.contains(id)) return -1L;
        // With clear-idle-on-activity=false the auto-Idle must stay "automatic": the old code
        // dropped the flag here, turning it into a manual Idle that was even saved to data.yml.
        if (!plugin.getConfig().getBoolean("auto-afk.clear-idle-on-activity", true)) return -1L;
        autoIdle.remove(id);
        if (statuses.remove(id, StatusType.IDLE)) {
            return previous == null ? 0L : Math.max(0L, now - previous);
        }
        return -1L;
    }

    public void recordActivity(Player player) {
        long idleMillis = markActive(player.getUniqueId());
        if (idleMillis < 0) return;
        announce(player.getName(), "clear");
        Messages messages = plugin.getMessages();
        if (plugin.getConfig().getBoolean("auto-afk.send-return-message", true)) {
            messages.send(player, "afk.returned", "{time}", Messages.formatDuration(idleMillis));
        }
        if (plugin.getConfig().getBoolean("auto-afk.broadcast-return", false)) {
            String text = messages.get(player, "afk.returned-broadcast",
                    "{player}", player.getName());
            if (!text.isEmpty()) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(player.getUniqueId())) other.sendMessage(text);
                }
            }
        }
    }

    public long getIdleMillis(UUID id) {
        Long last = lastActivity.get(id);
        if (last == null) {
            lastActivity.put(id, System.currentTimeMillis());
            return 0L;
        }
        return System.currentTimeMillis() - last;
    }

    // ------------------------------------------------------- join/quit/save

    /** Quit handling that also tells the other servers the player's status is gone. */
    public void handleQuit(UUID id, String playerName) {
        if (statuses.get(id) != null && playerName != null) {
            ProxyBridge bridge = plugin.getProxyBridge();
            if (bridge != null && bridge.isEnabled()) bridge.broadcastClear(playerName, id);
        }
        handleQuit(id);
    }

    public void handleQuit(UUID id) {
        StatusType status = statuses.get(id);
        if (status != null && !autoIdle.contains(id)) saved.put(id, status);
        else saved.remove(id);
        statuses.remove(id);
        lastActivity.remove(id);
        autoIdle.remove(id);
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override public void run() { save(); }
            });
        }
    }

    public StatusType restoreSaved(UUID id) {
        StatusType type = saved.remove(id);
        if (type != null) statuses.put(id, type);
        return type;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("statuses");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                // Locale.ROOT: with a Turkish default locale "idle".toUpperCase() is "IDLE" with a dotted I
                // and valueOf() failed, silently dropping every saved status.
                saved.put(UUID.fromString(key),
                        StatusType.valueOf(section.getString(key, "").toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save() {
        synchronized (fileLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, StatusType> e : new HashMap<UUID, StatusType>(saved).entrySet()) {
                yaml.set("statuses." + e.getKey(), e.getValue().name());
            }
            try {
                yaml.save(new File(plugin.getDataFolder(), "data.yml"));
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save data.yml: " + ex.getMessage());
            }
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, StatusType> e : statuses.entrySet()) {
            if (!autoIdle.contains(e.getKey())) saved.put(e.getKey(), e.getValue());
        }
        save();
    }
}
