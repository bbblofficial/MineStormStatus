package com.minestorm.status.command;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StatusCommand implements TabExecutor {

    private static final List<String> OPTIONS = List.of("busy", "idle", "away", "clear");

    private final MineStormStatus plugin;

    public StatusCommand(MineStormStatus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("players-only"));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(plugin.getMessages().get("usage"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "busy" -> apply(player, StatusType.BUSY);
            case "idle" -> apply(player, StatusType.IDLE);
            case "away" -> apply(player, StatusType.AWAY);
            case "clear" -> {
                plugin.getStatusManager().clearStatus(player.getUniqueId());
                player.sendMessage(plugin.getMessages().get("status-cleared"));
                player.sendActionBar(Component.empty());
            }
            default -> player.sendMessage(plugin.getMessages().get("usage"));
        }
        return true;
    }

    private void apply(Player player, StatusType type) {
        plugin.getStatusManager().setStatus(player.getUniqueId(), type);
        player.sendMessage(plugin.getMessages().get("status-" + type.getKey()));
        player.sendActionBar(plugin.getMessages().hud(type));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String option : OPTIONS) {
                if (option.startsWith(prefix)) {
                    result.add(option);
                }
            }
        }
        return result;
    }
}
