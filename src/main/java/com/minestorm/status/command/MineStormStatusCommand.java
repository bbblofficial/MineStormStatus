package com.minestorm.status.command;

import com.minestorm.status.MineStormStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /minestormstatus [help|busy|idle|away|clear|creator|info|reload] (alias /mss) */
public final class MineStormStatusCommand implements TabExecutor {

    private final MineStormStatus plugin;

    public MineStormStatusCommand(MineStormStatus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minestormstatus.command")) {
            sender.sendMessage(plugin.getMessages().get("general.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "creator" -> sender.sendMessage(plugin.getMessages().get("commands.creator"));
            case "info", "version" -> sender.sendMessage(plugin.getMessages().get(
                    "commands.info", Placeholder.unparsed("version", plugin.getVersion())));
            case "reload" -> {
                if (!sender.hasPermission("minestormstatus.reload")) {
                    sender.sendMessage(plugin.getMessages().get("general.no-permission"));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getMessages().get("commands.reload-success"));
            }
            case "busy", "idle", "away", "clear" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getMessages().get("general.players-only"));
                    return true;
                }
                if (!player.hasPermission("minestormstatus.use")) {
                    player.sendMessage(plugin.getMessages().get("general.no-permission"));
                    return true;
                }
                StatusCommand.runOption(player, sub, plugin);
            }
            default -> sender.sendMessage(plugin.getMessages().get("commands.unknown"));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        for (Component line : plugin.getMessages().list("commands.help")) {
            sender.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length != 1 || !sender.hasPermission("minestormstatus.command")) {
            return result;
        }
        List<String> options = new ArrayList<>(List.of("help", "creator", "info"));
        if (sender.hasPermission("minestormstatus.use")) {
            options.addAll(List.of("busy", "idle", "away", "clear"));
        }
        if (sender.hasPermission("minestormstatus.reload")) {
            options.add("reload");
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
