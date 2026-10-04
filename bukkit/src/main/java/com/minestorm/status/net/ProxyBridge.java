package com.minestorm.status.net;

import com.minestorm.status.MineStormStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Cross-server relay for status changes and mention alerts.
 * No database — each server keeps its own statuses.
 */
public final class ProxyBridge implements PluginMessageListener {

    private final MineStormStatus plugin;
    private boolean enabled;
    private String secret;

    public ProxyBridge(MineStormStatus plugin) { this.plugin = plugin; }

    public void enable() {
        secret = plugin.getConfig().getString("network.secret", "change-me");
        try {
            Messenger m = plugin.getServer().getMessenger();
            m.registerOutgoingPluginChannel(plugin, Net.CHANNEL);
            m.registerIncomingPluginChannel(plugin, Net.CHANNEL, this);
            enabled = true;
        } catch (Throwable t) {
            enabled = false;
            plugin.getLogger().warning("Plugin messaging unavailable: " + t.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }

    public void disable() {
        if (!enabled) return;
        Messenger m = plugin.getServer().getMessenger();
        try { m.unregisterOutgoingPluginChannel(plugin, Net.CHANNEL); } catch (Throwable ignored) {}
        try { m.unregisterIncomingPluginChannel(plugin, Net.CHANNEL, this); } catch (Throwable ignored) {}
        enabled = false;
    }

    public void broadcastStatus(String playerName, String statusKey) {
        if (enabled) send(Net.encode(secret, Net.STATUS, playerName, statusKey));
    }

    public void broadcastMention(String sender, String target, String statusKey) {
        if (enabled) send(Net.encode(secret, Net.MENTION, sender, target, statusKey));
    }

    private void send(byte[] data) {
        Player carrier = null;
        for (Player p : Bukkit.getOnlinePlayers()) { carrier = p; break; }
        if (carrier == null) return;
        try { carrier.sendPluginMessage(plugin, Net.CHANNEL, data); }
        catch (Throwable t) { plugin.getLogger().fine("Plugin message failed: " + t.getMessage()); }
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!enabled || !Net.CHANNEL.equals(channel)) return;
        String[] parts = Net.decode(message, secret);
        if (parts == null) return;
        String type = parts[0];

        // We don't need to react to remote status changes on this server
        // (each server's own players have their own status). We only need to
        // deliver mention alerts to the correct server.
        if (Net.MENTION.equals(type) && parts.length >= 4) {
            String senderName = parts[1];
            String statusKey = parts[3];
            Player sender = Bukkit.getPlayerExact(senderName);
            if (sender == null) return;
            String key = "busy".equals(statusKey) ? "alerts.busy" : "alerts.idle";
            plugin.getMessages().send(sender, key, "{player}", parts[2]);
        }
    }
}
