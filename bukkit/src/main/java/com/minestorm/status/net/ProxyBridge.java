package com.minestorm.status.net;

import com.minestorm.status.MineStormStatus;
import com.minestorm.status.manager.StatusType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-server relay for player statuses.
 *
 * No database: every backend keeps its OWN statuses and mirrors the statuses of the
 * OTHER backends in memory ("remote" map, name -> status). The proxy plugin only forwards
 * raw packets. Remote entries are kept fresh by:
 *   - STATUS packets (instant, sent whenever a status changes),
 *   - SYNC packets (heartbeat every network.sync-interval-seconds, also fixes lost packets),
 *   - REQUEST packets (a backend that just got a player asks the others for a SYNC),
 * and they expire after 3 missed heartbeats (crashed server / lost "clear").
 */
public final class ProxyBridge implements PluginMessageListener {

    public static final String DEFAULT_SECRET = "change-me-to-a-long-random-string";

    private static final int SYNC_CHUNK = 40;               // players per SYNC packet
    private static final long COOLDOWN_MS = 5000L;          // min gap between REQUEST / reply
    private static final long WARN_GAP_MS = 10L * 60L * 1000L;

    /** A status mirrored from another backend server. */
    public static final class RemoteStatus {
        public final String player;
        public final String origin;
        public final StatusType type;
        final long updated;

        RemoteStatus(String player, String origin, StatusType type, long updated) {
            this.player = player;
            this.origin = origin;
            this.type = type;
            this.updated = updated;
        }
    }

    private final MineStormStatus plugin;
    private final Map<String, RemoteStatus> remote = new ConcurrentHashMap<String, RemoteStatus>();

    private volatile boolean enabled;
    private volatile String secret = "";
    private volatile String serverName = "server";
    private volatile long ttlMillis = 90000L;
    private volatile long lastReceived;
    private volatile long lastSent;
    private volatile long lastReplyAt;
    private volatile long lastRequestAt;
    private volatile long lastCarrierWarn;
    private boolean registered;
    private BukkitTask syncTask;

    public ProxyBridge(MineStormStatus plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------ lifecycle

    /** (Re)initialises the relay from config.yml. Safe to call again after a reload. */
    public void enable() {
        disable();

        String configured = plugin.getConfig().getString("network.secret", "");
        if (!Net.isUsableSecret(configured)) {
            plugin.getLogger().warning("network.secret is empty - cross-server relay disabled. "
                    + "Use the same long random secret on every backend.");
            return;
        }
        if (DEFAULT_SECRET.equals(configured) || configured.length() < 16) {
            plugin.getLogger().warning("network.secret is the public default or very short. "
                    + "Anyone who knows it can forge relay packets - change it to a long random string.");
        }
        String name = plugin.getConfig().getString("network.server-name", "server");
        if (name == null || name.trim().isEmpty()) name = "server";
        name = name.trim();
        if ("server".equals(name)) {
            plugin.getLogger().warning("network.server-name is still 'server'. "
                    + "Give every backend a unique name (same as in the proxy config).");
        }
        long interval = Math.max(5L, plugin.getConfig().getLong("network.sync-interval-seconds", 30L));

        try {
            Messenger m = plugin.getServer().getMessenger();
            m.registerOutgoingPluginChannel(plugin, Net.CHANNEL);
            m.registerIncomingPluginChannel(plugin, Net.CHANNEL, this);
            registered = true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Plugin messaging unavailable: " + t.getMessage());
            unregisterChannels();
            return;
        }

        this.secret = configured;
        this.serverName = name;
        this.ttlMillis = interval * 3000L;
        this.enabled = true;

        long period = interval * 20L;
        this.syncTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { syncAll(); }
        }, period, period);
    }

    public boolean isEnabled() { return enabled; }

    public void disable() {
        enabled = false;
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        unregisterChannels();
        remote.clear();
    }

    private void unregisterChannels() {
        Messenger m = plugin.getServer().getMessenger();
        try { m.unregisterOutgoingPluginChannel(plugin, Net.CHANNEL); } catch (Throwable ignored) { }
        try { m.unregisterIncomingPluginChannel(plugin, Net.CHANNEL, this); } catch (Throwable ignored) { }
        registered = false;
    }

    // ------------------------------------------------------------ sending

    /** Announce a status change ("busy" / "idle" / "away" / "clear") to the other backends. */
    public void broadcastStatus(String playerName, String statusKey) {
        if (!enabled) return;
        send(Net.encode(secret, Net.STATUS, serverName, playerName, statusKey), null);
    }

    /** Same as broadcastStatus(name, "clear") but never uses the leaving player's connection. */
    public void broadcastClear(String playerName, UUID leaving) {
        if (!enabled) return;
        send(Net.encode(secret, Net.STATUS, serverName, playerName, "clear"), leaving);
    }

    /** Called when a player joins: if we know nothing about other servers yet, ask them. */
    public void requestSyncIfNeeded() {
        if (!enabled || !remote.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastRequestAt < COOLDOWN_MS) return;
        lastRequestAt = now;
        send(Net.encode(secret, Net.REQUEST, serverName), null);
    }

    private void send(final byte[] data, final UUID exclude) {
        if (Bukkit.isPrimaryThread()) {
            sendNow(data, exclude);
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { sendNow(data, exclude); }
            });
        }
    }

    private void sendNow(byte[] data, UUID exclude) {
        if (!enabled) return;
        Player carrier = null;
        boolean sawPlayer = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (exclude != null && p.getUniqueId().equals(exclude)) continue;
            sawPlayer = true;
            // The proxy plugin registers the channel for every player it moves to a backend.
            // A player without it cannot carry the message (Bukkit silently drops it).
            if (p.getListeningPluginChannels().contains(Net.CHANNEL)) { carrier = p; break; }
        }
        if (carrier == null) {
            if (sawPlayer) warnNoCarrier();
            return;
        }
        try {
            carrier.sendPluginMessage(plugin, Net.CHANNEL, data);
            lastSent = System.currentTimeMillis();
        } catch (Throwable t) {
            plugin.getLogger().fine("Plugin message failed: " + t.getMessage());
        }
    }

    private void warnNoCarrier() {
        long now = System.currentTimeMillis();
        if (now - lastCarrierWarn < WARN_GAP_MS) return;
        lastCarrierWarn = now;
        plugin.getLogger().warning("No online player has the '" + Net.CHANNEL + "' channel registered. "
                + "Install MineStormStatus-Bungee / -Velocity on the proxy (and restart it) - "
                + "without it nothing is relayed between servers. Run /mss debug for details.");
    }

    /** Heartbeat: re-send every local status. Main thread only. */
    private void syncAll() {
        if (!enabled) return;
        purgeExpired();
        List<String> pairs = new ArrayList<String>();
        for (Map.Entry<UUID, StatusType> e : plugin.getStatusManager().snapshot().entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            pairs.add(p.getName());
            pairs.add(e.getValue().getKey());
            if (pairs.size() >= SYNC_CHUNK * 2) {
                sendSync(pairs);
                pairs.clear();
            }
        }
        if (!pairs.isEmpty()) sendSync(pairs);
    }

    private void sendSync(List<String> pairs) {
        String[] args = new String[pairs.size() + 1];
        args[0] = serverName;
        for (int i = 0; i < pairs.size(); i++) args[i + 1] = pairs.get(i);
        send(Net.encode(secret, Net.SYNC, args), null);
    }

    // ------------------------------------------------------------ receiving

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!enabled || !Net.CHANNEL.equals(channel)) return;
        String[] p = Net.decode(message, secret);
        if (p == null || p.length < 2) return;
        lastReceived = System.currentTimeMillis();

        String type = p[0];
        String origin = p[1];
        if (Net.STATUS.equals(type) && p.length >= 4) {
            applyRemote(origin, p[2], p[3]);
        } else if (Net.SYNC.equals(type)) {
            for (int i = 2; i + 1 < p.length; i += 2) applyRemote(origin, p[i], p[i + 1]);
        } else if (Net.REQUEST.equals(type)) {
            replyToRequest();
        }
        // Net.MENTION (legacy) and unknown types are ignored on purpose.
    }

    private void applyRemote(String origin, String name, String key) {
        if (name == null || name.isEmpty()) return;
        String id = name.toLowerCase(Locale.ROOT);
        if ("clear".equals(key)) {
            // Only the server that owns the entry may clear it. This stops a late "clear"
            // from the old server wiping the status the new server just announced.
            RemoteStatus current = remote.get(id);
            if (current != null && current.origin.equals(origin)) remote.remove(id, current);
            return;
        }
        StatusType type = StatusType.parse(key);
        if (type == null) return;
        remote.put(id, new RemoteStatus(name, origin, type, System.currentTimeMillis()));
    }

    private void replyToRequest() {
        if (!Bukkit.isPrimaryThread()) {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { replyToRequest(); }
            });
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastReplyAt < COOLDOWN_MS) return;
        lastReplyAt = now;
        syncAll();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, RemoteStatus> e : remote.entrySet()) {
            if (now - e.getValue().updated > ttlMillis) remote.remove(e.getKey(), e.getValue());
        }
    }

    // ------------------------------------------------------------ queries

    /** Statuses of players that are on OTHER backends (expired entries removed). */
    public Collection<RemoteStatus> remoteSnapshot() {
        purgeExpired();
        return new ArrayList<RemoteStatus>(remote.values());
    }

    /** Lines for /minestormstatus debug (use & colour codes). */
    public List<String> debugLines() {
        List<String> lines = new ArrayList<String>();
        lines.add("&6&lMineStormStatus relay debug");
        lines.add("&7Relay enabled: " + (enabled ? "&ayes" : "&cno"));
        if (!enabled) {
            lines.add("&7Check network.proxy and network.secret in config.yml, then /mss reload.");
            return lines;
        }
        int online = 0;
        boolean carrier = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            online++;
            if (p.getListeningPluginChannels().contains(Net.CHANNEL)) carrier = true;
        }
        lines.add("&7server-name: &f" + serverName);
        lines.add("&7Proxy channel registered: " + (carrier ? "&ayes"
                : (online == 0 ? "&eunknown (no players online)" : "&cno - is the proxy plugin installed?")));
        lines.add("&7Statuses mirrored from other servers: &f" + remote.size());
        lines.add("&7Last packet received: &f" + ago(lastReceived));
        lines.add("&7Last packet sent: &f" + ago(lastSent));
        return lines;
    }

    private static String ago(long time) {
        if (time == 0L) return "never";
        return ((System.currentTimeMillis() - time) / 1000L) + "s ago";
    }
}
