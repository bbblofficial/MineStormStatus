package com.minestorm.status.task;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Re-sends the action bar HUD so it stays visible while a status is active. */
public final class ActionBarTask extends BukkitRunnable {

    private final MineStormStatus plugin;
    private final Set<UUID> shown = new HashSet<>();

    public ActionBarTask(MineStormStatus plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            StatusType status = plugin.getStatusManager().getStatus(id);
            if (status != null) {
                player.sendActionBar(plugin.getMessages().hud(status));
                shown.add(id);
            } else if (shown.remove(id)) {
                player.sendActionBar(Component.empty());
            }
        }
        shown.removeIf(id -> Bukkit.getPlayer(id) == null);
    }
}
