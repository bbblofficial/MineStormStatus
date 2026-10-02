package com.minestorm.status.manager;

import com.minestorm.status.MineStormStatus;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe storage for player statuses and activity timestamps
 * (chat events are fired asynchronously).
 */
public final class StatusManager {

    private final MineStormStatus plugin;
    private final Map<UUID, StatusType> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<UUID> autoIdle = ConcurrentHashMap.newKeySet();

    public StatusManager(MineStormStatus plugin) {
        this.plugin = plugin;
    }

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

    /** Immutable copy of all active statuses. */
    public Map<UUID, StatusType> snapshot() {
        return Map.copyOf(statuses);
    }

    /**
     * Records activity for a player.
     *
     * @return true if an automatic Idle status was removed by this activity
     */
    public boolean markActive(UUID id) {
        lastActivity.put(id, System.currentTimeMillis());
        if (!autoIdle.remove(id)) {
            return false;
        }
        if (plugin.getConfig().getBoolean("auto-afk.clear-idle-on-activity", true)) {
            return statuses.remove(id, StatusType.IDLE);
        }
        return false;
    }

    public long getIdleMillis(UUID id) {
        Long last = lastActivity.get(id);
        if (last == null) {
            lastActivity.put(id, System.currentTimeMillis());
            return 0L;
        }
        return System.currentTimeMillis() - last;
    }

    public void remove(UUID id) {
        statuses.remove(id);
        lastActivity.remove(id);
        autoIdle.remove(id);
    }
}
