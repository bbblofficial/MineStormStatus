package com.minestorm.status.task;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/** Re-sends the action bar HUD so it stays visible while a status is active. */
public final class ActionBarTask extends BukkitRunnable {

    private final MineStormStatus plugin;
    private final Set<UUID> shown = new HashSet<UUID>();

    public ActionBarTask(MineStormStatus plugin) { this.plugin = plugin; }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            StatusType status = plugin.getStatusManager().getStatus(id);
            if (status != null) {
                plugin.getActionBar().send(player, plugin.getMessages().hud(player, status));
                shown.add(id);
            } else if (shown.remove(id)) {
                plugin.getActionBar().clear(player);
            }
        }
        Iterator<UUID> iterator = shown.iterator();
        while (iterator.hasNext()) {
            if (Bukkit.getPlayer(iterator.next()) == null) iterator.remove();
        }
    }
}
