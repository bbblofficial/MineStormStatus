package com.minestorm.status.listener;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ChatListener implements Listener {

    private final MineStormStatus plugin;

    public ChatListener(MineStormStatus plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();

        // Chatting counts as activity.
        if (plugin.getStatusManager().markActive(sender.getUniqueId())) {
            sender.sendMessage(plugin.getMessages().get("auto-idle-cleared"));
        }

        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        int flags = plugin.getConfig().getBoolean("mention.case-sensitive", false)
                ? 0
                : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

        for (Map.Entry<UUID, StatusType> entry : plugin.getStatusManager().snapshot().entrySet()) {
            StatusType type = entry.getValue();
            if (type != StatusType.BUSY && type != StatusType.IDLE) {
                continue; // Away is visual only.
            }
            if (entry.getKey().equals(sender.getUniqueId())) {
                continue;
            }
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target == null) {
                continue;
            }

            Pattern pattern = Pattern.compile(
                    "(?<![A-Za-z0-9_])" + Pattern.quote(target.getName()) + "(?![A-Za-z0-9_])", flags);
            if (pattern.matcher(text).find()) {
                sender.sendMessage(plugin.getMessages().get(type == StatusType.BUSY ? "busy-alert" : "idle-alert"));
            }
        }
    }
}
