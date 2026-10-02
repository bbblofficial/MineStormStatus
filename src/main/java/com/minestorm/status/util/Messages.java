package com.minestorm.status.util;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads messages.yml (MiniMessage) with the jar's copy as fallback defaults,
 * so missing keys never break the plugin after an update.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final MineStormStatus plugin;
    private final Map<String, Component> cache = new ConcurrentHashMap<>();
    private volatile FileConfiguration messages = new YamlConfiguration();

    public Messages(MineStormStatus plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read default messages.yml: " + ex.getMessage());
        }
        this.messages = loaded;
        this.cache.clear();
    }

    private String raw(String key) {
        String value = messages.getString(key);
        return value != null ? value : "<red>Missing message: " + key + "</red>";
    }

    private TagResolver prefix() {
        return Placeholder.parsed("prefix", messages.getString("prefix", ""));
    }

    /** Parses a message; <prefix> and any extra resolvers (e.g. <player>) are applied. */
    public Component get(String key, TagResolver... resolvers) {
        if (resolvers.length == 0) {
            return cache.computeIfAbsent(key, k -> MINI.deserialize(raw(k), prefix()));
        }
        return MINI.deserialize(raw(key), TagResolver.resolver(prefix(), TagResolver.resolver(resolvers)));
    }

    /** Parses a list of messages (e.g. the help text). */
    public List<Component> list(String key) {
        List<Component> result = new ArrayList<>();
        for (String line : messages.getStringList(key)) {
            result.add(MINI.deserialize(line, prefix()));
        }
        return result;
    }

    public Component hud(StatusType type) {
        return get("hud." + type.getKey());
    }
}
