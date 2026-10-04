package com.minestorm.status.util;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads messages.yml (legacy + hex, {placeholders}, optional PlaceholderAPI). */
public final class Messages {

    private final MineStormStatus plugin;
    private volatile FileConfiguration messages = new YamlConfiguration();
    private boolean papiChecked;
    private Method papiMethod;

    public Messages(MineStormStatus plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);

        YamlConfiguration loaded = new YamlConfiguration();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            loaded.load(r);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().warning("Could not read messages.yml, using defaults: " + ex.getMessage());
        }

        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                loaded.setDefaults(defaults);
            }
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().warning("Could not read default messages.yml: " + ex.getMessage());
        }
        this.messages = loaded;
    }

    private String process(Player player, String text, String... replacements) {
        text = text.replace("{prefix}", messages.getString("prefix", ""));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        if (player != null) text = applyPlaceholderApi(player, text);
        return Colors.translate(text);
    }

    private String raw(String key) {
        String v = messages.getString(key);
        return v != null ? v : "&cMissing message: " + key;
    }

    public String get(String key, String... replacements) {
        return process(null, raw(key), replacements);
    }

    public String get(Player player, String key, String... replacements) {
        return process(player, raw(key), replacements);
    }

    public List<String> list(String key, String... replacements) {
        List<String> result = new ArrayList<String>();
        for (String line : messages.getStringList(key)) result.add(process(null, line, replacements));
        return result;
    }

    public String hud(Player player, StatusType type) { return get(player, "hud." + type.getKey()); }

    public String statusName(StatusType type) { return get("status-names." + type.getKey()); }

    public void send(CommandSender sender, String key, String... replacements) {
        String text = sender instanceof Player
                ? get((Player) sender, key, replacements) : get(key, replacements);
        if (!text.isEmpty()) sender.sendMessage(text);
    }

    public void sendList(CommandSender sender, String key, String... replacements) {
        for (String line : list(key, replacements)) if (!line.isEmpty()) sender.sendMessage(line);
    }

    private String applyPlaceholderApi(Player player, String text) {
        if (!plugin.getConfig().getBoolean("use-placeholderapi", true) || !Bukkit.isPrimaryThread()) {
            return text;
        }
        if (!papiChecked) {
            papiChecked = true;
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    papiMethod = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                            .getMethod("setPlaceholders", Player.class, String.class);
                } catch (Throwable ignored) { papiMethod = null; }
            }
        }
        if (papiMethod == null) return text;
        try {
            Object r = papiMethod.invoke(null, player, text);
            return r instanceof String ? (String) r : text;
        } catch (Throwable ignored) { return text; }
    }

    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
