package com.minestorm.status.command;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.net.ProxyBridge;
import com.minestorm.status.util.Colors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /minestormstatus [help|busy|idle|away|clear|creator|info|reload|debug] (alias /mss) */
public final class MineStormStatusCommand implements CommandExecutor, TabCompleter {

    private final MineStormStatus plugin;

    public MineStormStatusCommand(MineStormStatus plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minestormstatus.command")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.getMessages().sendList(sender, "commands.help");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            plugin.getMessages().sendList(sender, "commands.help");
        } else if (sub.equals("creator")) {
            plugin.getMessages().send(sender, "commands.creator");
        } else if (sub.equals("info") || sub.equals("version")) {
            plugin.getMessages().send(sender, "commands.info", "{version}", plugin.getVersion());
        } else if (sub.equals("reload")) {
            if (!sender.hasPermission("minestormstatus.reload")) {
                plugin.getMessages().send(sender, "general.no-permission");
                return true;
            }
            plugin.reloadPlugin();
            plugin.getMessages().send(sender, "commands.reload-success");
        } else if (sub.equals("debug")) {
            // Admin helper: shows whether the proxy <-> backend relay is really connected.
            if (!sender.hasPermission("minestormstatus.reload")) {
                plugin.getMessages().send(sender, "general.no-permission");
                return true;
            }
            ProxyBridge bridge = plugin.getProxyBridge();
            List<String> lines = bridge == null
                    ? Arrays.asList("&cRelay is not initialised.") : bridge.debugLines();
            for (String line : lines) sender.sendMessage(Colors.translate(line));
        } else if (sub.equals("busy") || sub.equals("idle") || sub.equals("away") || sub.equals("clear")) {
            if (!(sender instanceof Player)) {
                plugin.getMessages().send(sender, "general.players-only");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("minestormstatus.use")) {
                plugin.getMessages().send(player, "general.no-permission");
                return true;
            }
            StatusCommand.runOption(player, sub, plugin);
        } else {
            plugin.getMessages().send(sender, "commands.unknown");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<String>();
        if (args.length != 1 || !sender.hasPermission("minestormstatus.command")) return result;
        List<String> options = new ArrayList<String>(Arrays.asList("help", "creator", "info"));
        if (sender.hasPermission("minestormstatus.use"))
            options.addAll(Arrays.asList("busy", "idle", "away", "clear"));
        if (sender.hasPermission("minestormstatus.reload")) options.addAll(Arrays.asList("reload", "debug"));
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (String option : options) if (option.startsWith(prefix)) result.add(option);
        return result;
    }
}
