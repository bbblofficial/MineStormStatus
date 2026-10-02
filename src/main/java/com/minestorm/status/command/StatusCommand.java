package com.minestorm.status.command;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /status busy|idle|away|clear */
public final class StatusCommand implements TabExecutor {

    private static final List<String> OPTIONS = List.of("busy", "idle", "away", "clear");

    private final MineStormStatus plugin;

    public StatusCommand(MineStormStatus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!player.hasPermission("minestormstatus.use")) {
            player.sendMessage(plugin.getMessages().get("general.no-permission"));
            return true;
        }
        if (args.length != 1 || !runOption(player, args[0])) {
            player.sendMessage(plugin.getMessages().get("status.usage"));
        }
        return true;
    }

    /** Applies busy/idle/away/clear. Returns false if the option is unknown. */
    static boolean runOption(Player player, String option, MineStormStatus plugin) {
        switch (option.toLowerCase(Locale.ROOT)) {
            case "busy" -> plugin.getStatusManager().applyAndNotify(player, StatusType.BUSY);
            case "idle" -> plugin.getStatusManager().applyAndNotify(player, StatusType.IDLE);
            case "away" -> plugin.getStatusManager().applyAndNotify(player, StatusType.AWAY);
            case "clear" -> plugin.getStatusManager().clearAndNotify(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean runOption(Player player, String option) {
        return runOption(player, option, plugin);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("minestormstatus.use")) {
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
