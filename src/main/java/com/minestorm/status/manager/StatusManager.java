package com.minestorm.status.manager;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe storage for player statuses and activity timestamps
 * (chat events are fired asynchronously on 1.8.x - 1.19).
 */
public final class StatusManager {

    private final MineStormStatus plugin;
    private final Map<UUID, StatusType> statuses = new ConcurrentHashMap<UUID, StatusType>();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<UUID, Long>();
    private final Set<UUID> autoIdle = ConcurrentHashMap.newKeySet();
    /** Manual statuses of players who left the server (restored when they come back). */
    private final Map<UUID, StatusType> saved = new ConcurrentHashMap<UUID, StatusType>();
    private final Object fileLock = new Object();

    public StatusManager(MineStormStatus plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- statuses

    public StatusType getStatus(UUID id) {
        return statuses.get(id);
    }

    /** Manually set a status. */
    public void setStatus(UUID id, StatusType type) {
        autoIdle.remove(id);
        statuses.put(id, type);
    }

    /** Set Idle as a result of the AFK timer. */
    public void setAutoIdle(UUID id) {
        autoIdle.add(id);
        statuses.put(id, StatusType.IDLE);
    }

    public void clearStatus(UUID id) {
        autoIdle.remove(id);
        statuses.remove(id);
    }

    /** Sets a status and sends the configured confirmation message + HUD. */
    public void applyAndNotify(Player player, StatusType type) {
        setStatus(player.getUniqueId(), type);
        Messages messages = plugin.getMessages();
        messages.send(player, "status.set." + type.getKey());
        if (plugin.getConfig().getBoolean("action-bar.enabled", true)) {
            plugin.getActionBar().send(player, messages.hud(player, type));
        }
    }

    /** Clears the status and sends the configured message. */
    public void clearAndNotify(Player player) {
        UUID id = player.getUniqueId();
        if (getStatus(id) == null) {
            plugin.getMessages().send(player, "status.nothing-to-clear");
            return;
        }
        clearStatus(id);
        plugin.getMessages().send(player, "status.cleared");
        plugin.getActionBar().clear(player);
    }

    /** Immutable copy of all active statuses. */
    public Map<UUID, StatusType> snapshot() {
        return new HashMap<UUID, StatusType>(statuses);
    }

    // ---------------------------------------------------------------- activity

    /**
     * Records activity for a player.
     *
     * @return how long (ms) the player was idle if an automatic Idle status was removed by this
     *         activity (i.e. the player came back), otherwise -1
     */
    public long markActive(UUID id) {
        long now = System.currentTimeMillis();
        Long previous = lastActivity.put(id, now);
        if (!autoIdle.remove(id)) {
            return -1L;
        }
        if (plugin.getConfig().getBoolean("auto-afk.clear-idle-on-activity", true)
                && statuses.remove(id, StatusType.IDLE)) {
            return previous == null ? 0L : Math.max(0L, now - previous);
        }
        return -1L;
    }

    /** Records activity and handles the "player came back" messages. */
    public void recordActivity(Player player) {
        long idleMillis = markActive(player.getUniqueId());
        if (idleMillis < 0) {
            return;
        }
        Messages messages = plugin.getMessages();
        if (plugin.getConfig().getBoolean("auto-afk.send-return-message", true)) {
            messages.send(player, "afk.returned", "{time}", Messages.formatDuration(idleMillis));
        }
        if (plugin.getConfig().getBoolean("auto-afk.broadcast-return", false)) {
            String text = messages.get(player, "afk.returned-broadcast", "{player}", player.getName());
            if (!text.isEmpty()) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(player.getUniqueId())) {
                        other.sendMessage(text);
                    }
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

    // ------------------------------------------------------ join / quit / saving

    /** Called when a player leaves: remembers a manual status, forgets everything else. */
    public void handleQuit(UUID id) {
        StatusType status = statuses.get(id);
        if (status != null && !autoIdle.contains(id)) {
            saved.put(id, status);
        } else {
            saved.remove(id);
        }
        statuses.remove(id);
        lastActivity.remove(id);
        autoIdle.remove(id);

        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    save();
                }
            });
        }
    }

    /** Restores the status a player had when they left. Returns it, or null if none. */
    public StatusType restoreSaved(UUID id) {
        StatusType type = saved.remove(id);
        if (type != null) {
            statuses.put(id, type);
        }
        return type;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("statuses");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                saved.put(UUID.fromString(key), StatusType.valueOf(section.getString(key, "").toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // invalid entry, skip it
            }
        }
    }

    public void save() {
        synchronized (fileLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, StatusType> entry : new HashMap<UUID, StatusType>(saved).entrySet()) {
                yaml.set("statuses." + entry.getKey(), entry.getValue().name());
            }
            try {
                yaml.save(new File(plugin.getDataFolder(), "data.yml"));
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save data.yml: " + ex.getMessage());
            }
        }
    }

    /** Called on plugin disable: keeps manual statuses of online players and writes data.yml. */
    public void shutdown() {
        for (Map.Entry<UUID, StatusType> entry : statuses.entrySet()) {
            if (!autoIdle.contains(entry.getKey())) {
                saved.put(entry.getKey(), entry.getValue());
            }
        }
        save();
    }
}
