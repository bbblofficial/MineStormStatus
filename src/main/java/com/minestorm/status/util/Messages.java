package com.minestorm.status.util;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads MiniMessage strings from config.yml (section "messages") and caches the parsed components. */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final MineStormStatus plugin;
    private final Map<String, Component> cache = new ConcurrentHashMap<>();

    public Messages(MineStormStatus plugin) {
        this.plugin = plugin;
    }

    public Component get(String key) {
        return cache.computeIfAbsent(key, k -> MINI.deserialize(
                plugin.getConfig().getString("messages." + k, "<red>Missing message: " + k + "</red>")));
    }

    public Component hud(StatusType type) {
        return get("hud-" + type.getKey());
    }
}
