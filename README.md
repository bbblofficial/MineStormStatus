# MineStormStatus

Player status (Busy / Idle / Away), auto-AFK, mention alerts and an action bar HUD
for **Spigot / Paper / Purpur / CraftBukkit forks, 1.8.8 and newer**.

Multi-platform: backend plugin + optional **BungeeCord** / **Velocity** relay
so mentions and status changes carry across servers.

> Created by **Muvixo**

## No database

Each backend keeps its own statuses in memory and persists them to `data.yml`.
The relay only forwards packets — there is **no shared database**. This means
each server has its own idea of who is Busy/Idle/Away, but mention alerts and
status changes propagate across the whole network in real time.

## Install

1. Put **`MineStormStatus-Bukkit-1.0.0-java17.jar`** (or the java21 build) in
   every backend's `plugins/` folder.
2. Optionally put **`MineStormStatus-Bungee-1.0.0.jar`** or
   **`MineStormStatus-Velocity-1.0.0.jar`** on the proxy.
3. Edit `config.yml` on every backend:
   ```yaml
   network:
     proxy: true                  # false = single server, no relay
     server-name: lobby           # unique per backend
     secret: "a long random string, identical on every server"
   ```
4. Restart.

## Commands

| Command | Permission |
|---|---|
| `/status busy\|idle\|away\|clear` | `minestormstatus.use` |
| `/minestormstatus` (`/mss`) help | `minestormstatus.command` |
| `/minestormstatus busy\|idle\|away\|clear` | `minestormstatus.use` |
| `/minestormstatus creator` | `minestormstatus.command` |
| `/minestormstatus info` | `minestormstatus.command` |
| `/minestormstatus reload` | `minestormstatus.reload` |

`minestormstatus.afk.bypass` exempts a player from auto-AFK.

## Build (two jars)

```bash
mvn clean package -Pjava17   # requires JDK 17
mvn clean package -Pjava21   # requires JDK 21
```

Artifacts:
- `bukkit/target/MineStormStatus-Bukkit-1.0.0-java17.jar`
- `bukkit/target/MineStormStatus-Bukkit-1.0.0-java21.jar`
- `bungee/target/MineStormStatus-Bungee-1.0.0.jar`
- `velocity/target/MineStormStatus-Velocity-1.0.0.jar`

## Cross-server relay (how it works)

The proxy plugin (Bungee / Velocity) is a dumb, stateless pipe: it forwards every packet a backend
sends to all OTHER backends. All logic lives in the backend plugin:

- every backend keeps its own statuses and **mirrors the statuses of the other backends in memory**
  (nothing is shared or stored in a database);
- a status change is sent instantly (`STATUS`), and every `network.sync-interval-seconds` the
  backend re-sends all its statuses (`SYNC`) so lost packets and restarts heal themselves;
- a backend that just got a player asks the others for a `SYNC` (`REQUEST`);
- mirrored entries expire after 3 missed heartbeats (crashed server, lost "clear");
- mention alerts therefore also work for Busy / Idle players on other servers
  (`network.cross-server-mentions`).

Requirements: the proxy plugin must be installed on the proxy, `network.secret` must be identical on
every backend and `network.server-name` unique. Run `/mss debug` on a backend to see whether the
relay is connected (needs `minestormstatus.reload`).

## Java 8 servers (1.8.8 - 1.16)

The default builds target Java 17 / 21. A server running on Java 8 cannot load them. Build the
backend jar for Java 8 with:

```bash
mvn clean package -Pjava8 -pl common,bukkit
```
