package com.minestorm.status.listener;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
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
        FileConfiguration config = plugin.getConfig();
        Player sender = event.getPlayer();

        if (config.getBoolean("auto-afk.activity.chat", true)) {
            plugin.getStatusManager().recordActivity(sender);
        }
        if (!config.getBoolean("mention.enabled", true)) {
            return;
        }

        boolean alertBusy = config.getBoolean("mention.alert-busy", true);
        boolean alertIdle = config.getBoolean("mention.alert-idle", true);
        int flags = config.getBoolean("mention.case-sensitive", false)
                ? 0
                : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());

        for (Map.Entry<UUID, StatusType> entry : plugin.getStatusManager().snapshot().entrySet()) {
            StatusType type = entry.getValue();
            // Away is visual only.
            if (type == StatusType.BUSY && !alertBusy) {
                continue;
            }
            if (type == StatusType.IDLE && !alertIdle) {
                continue;
            }
            if (type == StatusType.AWAY) {
                continue;
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
                sender.sendMessage(plugin.getMessages().get(
                        type == StatusType.BUSY ? "alerts.busy" : "alerts.idle",
                        Placeholder.unparsed("player", target.getName())));
            }
        }
    }
}
