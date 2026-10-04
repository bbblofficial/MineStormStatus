package com.minestorm.status.bungee;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

/**
 * MineStormStatus proxy relay (BungeeCord) — stateless.
 * Forwards any packet sent by a backend to every OTHER backend.
 * Packets coming from clients are dropped (anti-spoofing).
 */
public final class MineStormStatusBungee extends Plugin implements Listener {

    public static final String CHANNEL = "msstatus:main";

    @Override
    public void onEnable() {
        getProxy().registerChannel(CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);
        getLogger().info("MineStormStatus-Bungee enabled. Created by Muvixo.");
    }

    @Override
    public void onDisable() {
        getProxy().unregisterChannel(CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent e) {
        if (!CHANNEL.equals(e.getTag())) return;
        e.setCancelled(true);
        if (!(e.getSender() instanceof Server)) return;

        Server origin = (Server) e.getSender();
        String originName = origin.getInfo().getName();
        byte[] data = e.getData();

        for (ServerInfo info : getProxy().getServers().values()) {
            if (info.getName().equals(originName)) continue;
            if (info.getPlayers().isEmpty()) continue;
            info.sendData(CHANNEL, data, false);
        }
    }
}
