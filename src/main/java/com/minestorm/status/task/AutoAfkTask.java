package com.minestorm.status.task;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusManager;
import com.minestorm.status.manager.StatusType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/** Switches inactive players to Idle after the configured timeout. */
public final class AutoAfkTask extends BukkitRunnable {

    private final MineStormStatus plugin;

    public AutoAfkTask(MineStormStatus plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        FileConfiguration config = plugin.getConfig();
        StatusManager manager = plugin.getStatusManager();
        long timeoutMillis = Math.max(1L, config.getLong("auto-afk.idle-timeout-seconds", 300L)) * 1000L;
        boolean override = config.getBoolean("auto-afk.override-existing-status", false);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("minestormstatus.afk.bypass")) {
                continue;
            }
            UUID id = player.getUniqueId();
            StatusType current = manager.getStatus(id);
            boolean eligible = current == null || (override && current != StatusType.IDLE);
            if (!eligible || manager.getIdleMillis(id) < timeoutMillis) {
                continue;
            }

            manager.setAutoIdle(id);
            player.sendMessage(plugin.getMessages().get("auto-idle"));
            player.sendActionBar(plugin.getMessages().hud(StatusType.IDLE));
        }
    }
}
