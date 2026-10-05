package com.minestorm.status.listener;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import com.minestorm.status.net.ProxyBridge;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ChatListener implements Listener {

    private final MineStormStatus plugin;

    public ChatListener(MineStormStatus plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        FileConfiguration config = plugin.getConfig();
        final Player sender = event.getPlayer();

        if (config.getBoolean("auto-afk.activity.chat", true)) {
            plugin.getStatusManager().recordActivity(sender);
        }
        if (!config.getBoolean("mention.enabled", true)) return;

        final String text = ChatColor.stripColor(event.getMessage());
        if (text == null || text.isEmpty() || !plugin.isEnabled()) return;

        // Chat is async on most servers: look at players/statuses on the main thread, one tick
        // later, so the alert also appears AFTER the chat line instead of before it.
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() { processMentions(sender, text); }
        });
    }

    private void processMentions(Player sender, String text) {
        if (!sender.isOnline()) return;
        FileConfiguration config = plugin.getConfig();
        boolean alertBusy = config.getBoolean("mention.alert-busy", true);
        boolean alertIdle = config.getBoolean("mention.alert-idle", true);
        boolean caseSensitive = config.getBoolean("mention.case-sensitive", false);
        Set<String> done = new HashSet<String>();

        // 1) players on THIS server
        for (Map.Entry<UUID, StatusType> entry : plugin.getStatusManager().snapshot().entrySet()) {
            if (entry.getKey().equals(sender.getUniqueId())) continue;
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target == null) continue;
            tryAlert(sender, text, target.getName(), entry.getValue(),
                    alertBusy, alertIdle, caseSensitive, done);
        }

        // 2) players on OTHER servers (statuses mirrored through the proxy relay)
        ProxyBridge bridge = plugin.getProxyBridge();
        if (bridge != null && bridge.isEnabled() && config.getBoolean("network.cross-server-mentions", true)) {
            for (ProxyBridge.RemoteStatus remote : bridge.remoteSnapshot()) {
                if (remote.player.equalsIgnoreCase(sender.getName())) continue;
                if (Bukkit.getPlayerExact(remote.player) != null) continue; // local data wins
                tryAlert(sender, text, remote.player, remote.type,
                        alertBusy, alertIdle, caseSensitive, done);
            }
        }
    }

    private void tryAlert(Player sender, String text, String name, StatusType type,
                          boolean alertBusy, boolean alertIdle, boolean caseSensitive, Set<String> done) {
        if (type == StatusType.AWAY) return;
        if (type == StatusType.BUSY && !alertBusy) return;
        if (type == StatusType.IDLE && !alertIdle) return;
        if (!mentions(text, name, caseSensitive)) return;
        if (!done.add(name.toLowerCase(Locale.ROOT))) return;
        plugin.getMessages().send(sender,
                type == StatusType.BUSY ? "alerts.busy" : "alerts.idle",
                "{player}", name);
    }

    /** Cheap contains() first; the regex (whole-word check) only runs when the name appears at all. */
    private static boolean mentions(String text, String name, boolean caseSensitive) {
        if (name == null || name.isEmpty()) return false;
        String haystack = caseSensitive ? text : text.toLowerCase(Locale.ROOT);
        String needle = caseSensitive ? name : name.toLowerCase(Locale.ROOT);
        if (!haystack.contains(needle)) return false;
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern pattern = Pattern.compile(
                "(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])", flags);
        return pattern.matcher(text).find();
    }
}
