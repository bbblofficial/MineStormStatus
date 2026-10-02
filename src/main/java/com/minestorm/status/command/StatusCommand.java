package com.minestorm.status.command;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /status busy|idle|away|clear */
public final class StatusCommand implements CommandExecutor, TabCompleter {

    private static final List<String> OPTIONS = Arrays.asList("busy", "idle", "away", "clear");

    private final MineStormStatus plugin;

    public StatusCommand(MineStormStatus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessages().send(sender, "general.players-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("minestormstatus.use")) {
            plugin.getMessages().send(player, "general.no-permission");
            return true;
        }
        if (args.length != 1 || !runOption(player, args[0], plugin)) {
            plugin.getMessages().send(player, "status.usage");
        }
        return true;
    }

    /** Applies busy/idle/away/clear. Returns false if the option is unknown. */
    static boolean runOption(Player player, String option, MineStormStatus plugin) {
        String value = option.toLowerCase(Locale.ROOT);
        if (value.equals("busy")) {
            plugin.getStatusManager().applyAndNotify(player, StatusType.BUSY);
        } else if (value.equals("idle")) {
            plugin.getStatusManager().applyAndNotify(player, StatusType.IDLE);
        } else if (value.equals("away")) {
            plugin.getStatusManager().applyAndNotify(player, StatusType.AWAY);
        } else if (value.equals("clear")) {
            plugin.getStatusManager().clearAndNotify(player);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<String>();
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
