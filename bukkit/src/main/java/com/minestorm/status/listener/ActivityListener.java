package com.minestorm.status.listener;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusManager;
import com.minestorm.status.manager.StatusType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/** Tracks activity (resets the inactivity timer) and handles join/quit. */
public final class ActivityListener implements Listener {

    private final MineStormStatus plugin;

    public ActivityListener(MineStormStatus plugin) { this.plugin = plugin; }

    private boolean enabled(String key) {
        return plugin.getConfig().getBoolean("auto-afk.activity." + key, true);
    }

    private void active(Player player, String key) {
        if (enabled(key)) plugin.getStatusManager().recordActivity(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        StatusManager manager = plugin.getStatusManager();
        manager.markActive(player.getUniqueId());

        StatusType restored = null;
        if (plugin.getConfig().getBoolean("join.restore-status", true)) {
            restored = manager.restoreSaved(player.getUniqueId());
        }
        final StatusType restoredStatus = restored;
        final boolean welcome = plugin.getConfig().getBoolean("join.welcome-back", true)
                && player.hasPlayedBefore();

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                if (welcome) {
                    plugin.getMessages().send(player, "join.welcome-back",
                            "{player}", player.getName());
                }
                if (restoredStatus != null) {
                    plugin.getMessages().send(player, "join.status-restored",
                            "{status}", plugin.getMessages().statusName(restoredStatus));
                    if (plugin.getConfig().getBoolean("action-bar.enabled", true)) {
                        plugin.getActionBar().send(player,
                                plugin.getMessages().hud(player, restoredStatus));
                    }
                }
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getStatusManager().handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        boolean rotated = from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();
        if (moved) active(event.getPlayer(), "movement");
        else if (rotated) active(event.getPlayer(), "rotation");
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) active(e.getPlayer(), "interaction");
    }
    @EventHandler public void onInteractEntity(PlayerInteractEntityEvent e) { active(e.getPlayer(), "interaction"); }
    @EventHandler public void onSneak(PlayerToggleSneakEvent e) { active(e.getPlayer(), "interaction"); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { active(e.getPlayer(), "interaction"); }
    @EventHandler public void onHeldItem(PlayerItemHeldEvent e) { active(e.getPlayer(), "interaction"); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent e) { active(e.getPlayer(), "commands"); }
    @EventHandler public void onBlockBreak(BlockBreakEvent e) { active(e.getPlayer(), "blocks"); }
    @EventHandler public void onBlockPlace(BlockPlaceEvent e) { active(e.getPlayer(), "blocks"); }
    @EventHandler public void onInventoryClick(InventoryClickEvent e) {
        HumanEntity c = e.getWhoClicked();
        if (c instanceof Player) active((Player) c, "inventory");
    }
}
