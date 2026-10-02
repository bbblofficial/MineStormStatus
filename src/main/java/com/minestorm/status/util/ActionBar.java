package com.minestorm.status.util;

import com.minestorm.status.MineStormStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Sends action bar text on every server version without Adventure:
 * - Spigot/Paper 1.9+ : Player.Spigot#sendMessage(ChatMessageType.ACTION_BAR, ...)
 * - 1.8.x             : PacketPlayOutChat (type 2) via reflection
 */
public final class ActionBar {

    private final MineStormStatus plugin;
    private boolean ready;
    private boolean modern;
    private boolean warned;

    // modern (1.9+)
    private Object actionBarType;
    private Method fromLegacyText;
    private Method spigotSend;

    // legacy (1.8.x NMS)
    private Method getHandle;
    private Field connectionField;
    private Method sendPacket;
    private Method serialize;
    private Constructor<?> packetConstructor;

    public ActionBar(MineStormStatus plugin) {
        this.plugin = plugin;
        try {
            initModern();
            modern = true;
            ready = true;
            return;
        } catch (Throwable ignored) {
            // not a 1.9+ server, try 1.8.x
        }
        try {
            initLegacy();
            ready = true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Action bar is not supported on this server version: " + t);
        }
    }

    private void initModern() throws Exception {
        Class<?> type = Class.forName("net.md_5.bungee.api.ChatMessageType");
        Class<?> text = Class.forName("net.md_5.bungee.api.chat.TextComponent");
        Class<?> base = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
        actionBarType = type.getField("ACTION_BAR").get(null);
        fromLegacyText = text.getMethod("fromLegacyText", String.class);
        Class<?> baseArray = Array.newInstance(base, 0).getClass();
        spigotSend = Player.Spigot.class.getMethod("sendMessage", type, baseArray);
    }

    private void initLegacy() throws Exception {
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        String version = pkg.substring(pkg.lastIndexOf('.') + 1);
        String nms = "net.minecraft.server." + version + ".";

        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
        getHandle = craftPlayer.getMethod("getHandle");
        connectionField = Class.forName(nms + "EntityPlayer").getField("playerConnection");
        Class<?> packet = Class.forName(nms + "Packet");
        sendPacket = connectionField.getType().getMethod("sendPacket", packet);
        Class<?> component = Class.forName(nms + "IChatBaseComponent");
        serialize = Class.forName(nms + "IChatBaseComponent$ChatSerializer").getMethod("a", String.class);
        packetConstructor = Class.forName(nms + "PacketPlayOutChat").getConstructor(component, byte.class);
    }

    public void send(Player player, String text) {
        if (!ready || player == null || text == null) {
            return;
        }
        try {
            if (modern) {
                Object components = fromLegacyText.invoke(null, text);
                spigotSend.invoke(player.spigot(), actionBarType, components);
            } else {
                Object handle = getHandle.invoke(player);
                Object connection = connectionField.get(handle);
                Object component = serialize.invoke(null, "{\"text\":\"" + escape(text) + "\"}");
                Object packet = packetConstructor.newInstance(component, (byte) 2);
                sendPacket.invoke(connection, packet);
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Could not send action bar: " + t);
            }
        }
    }

    public void clear(Player player) {
        send(player, " ");
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
