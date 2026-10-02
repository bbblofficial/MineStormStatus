package com.minestorm.status.listener;

import com.minestorm.status.MineStormStatus;
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

/** Tracks player movement/interaction to reset the inactivity timer (toggles in config.yml). */
public final class ActivityListener implements Listener {

    private final MineStormStatus plugin;

    public ActivityListener(MineStormStatus plugin) {
        this.plugin = plugin;
    }

    private boolean enabled(String key) {
        return plugin.getConfig().getBoolean("auto-afk.activity." + key, true);
    }

    private void active(Player player, String key) {
        if (enabled(key)) {
            plugin.getStatusManager().recordActivity(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getStatusManager().markActive(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getStatusManager().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        boolean rotated = from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();
        if (moved) {
            active(event.getPlayer(), "movement");
        } else if (rotated) {
            active(event.getPlayer(), "rotation");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            active(event.getPlayer(), "interaction");
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        active(event.getPlayer(), "interaction");
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        active(event.getPlayer(), "interaction");
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        active(event.getPlayer(), "interaction");
    }

    @EventHandler
    public void onHeldItem(PlayerItemHeldEvent event) {
        active(event.getPlayer(), "interaction");
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        active(event.getPlayer(), "commands");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        active(event.getPlayer(), "blocks");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        active(event.getPlayer(), "blocks");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (clicker instanceof Player player) {
            active(player, "inventory");
        }
    }
}
