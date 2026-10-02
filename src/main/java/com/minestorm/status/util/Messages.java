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

/**
 * Loads messages.yml (legacy &amp; colors, &amp;#RRGGBB hex, {placeholders}, optional PlaceholderAPI).
 * The jar's copy is used as fallback for missing keys, so updates never break existing files.
 */
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
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        YamlConfiguration loaded = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            loaded.load(reader);
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

    // ------------------------------------------------------------------ building

    private String process(Player player, String text, String... replacements) {
        text = text.replace("{prefix}", messages.getString("prefix", ""));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        if (player != null) {
            text = applyPlaceholderApi(player, text);
        }
        return Colors.translate(text);
    }

    private String raw(String key) {
        String value = messages.getString(key);
        return value != null ? value : "&cMissing message: " + key;
    }

    /** Message with colors + {placeholders} applied. Replacements are pairs: "{key}", "value". */
    public String get(String key, String... replacements) {
        return process(null, raw(key), replacements);
    }

    /** Same as above, plus PlaceholderAPI for the given player. */
    public String get(Player player, String key, String... replacements) {
        return process(player, raw(key), replacements);
    }

    public List<String> list(String key, String... replacements) {
        List<String> result = new ArrayList<String>();
        for (String line : messages.getStringList(key)) {
            result.add(process(null, line, replacements));
        }
        return result;
    }

    public String hud(Player player, StatusType type) {
        return get(player, "hud." + type.getKey());
    }

    /** Colored status name from "status-names". */
    public String statusName(StatusType type) {
        return get("status-names." + type.getKey());
    }

    // ------------------------------------------------------------------- sending

    /** Sends a message. An empty message is not sent. */
    public void send(CommandSender sender, String key, String... replacements) {
        String text = sender instanceof Player
                ? get((Player) sender, key, replacements)
                : get(key, replacements);
        if (!text.isEmpty()) {
            sender.sendMessage(text);
        }
    }

    public void sendList(CommandSender sender, String key, String... replacements) {
        for (String line : list(key, replacements)) {
            if (!line.isEmpty()) {
                sender.sendMessage(line);
            }
        }
    }

    // ------------------------------------------------------------------- helpers

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
                } catch (Throwable ignored) {
                    papiMethod = null;
                }
            }
        }
        if (papiMethod == null) {
            return text;
        }
        try {
            Object result = papiMethod.invoke(null, player, text);
            return result instanceof String ? (String) result : text;
        } catch (Throwable ignored) {
            return text;
        }
    }

    /** 3725000 ms -> "1h 2m", 312000 ms -> "5m 12s", 8000 ms -> "8s". */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
