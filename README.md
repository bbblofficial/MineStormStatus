# MineStormStatus

Status plugin by Muvixo for **Spigot / Paper / Purpur / CraftBukkit forks, 1.8.8 and newer**.
Busy / Idle / Away statuses, mention alerts, auto-AFK and a persistent action bar HUD.

## Commands
| Command | Description | Permission |
|---|---|---|
| `/status busy\|idle\|away\|clear` | Set / clear your status | `minestormstatus.use` |
| `/minestormstatus` (`/mss`) | Help | `minestormstatus.command` |
| `/minestormstatus busy\|idle\|away\|clear` | Same as `/status` | `minestormstatus.use` |
| `/minestormstatus creator` | Shows the creator | `minestormstatus.command` |
| `/minestormstatus info` | Plugin version | `minestormstatus.command` |
| `/minestormstatus reload` | Reload config.yml + messages.yml | `minestormstatus.reload` |

`minestormstatus.afk.bypass` exempts a player from auto-AFK.

## Colors (messages.yml)
- Classic codes: `&a`, `&c&l`, `&r` ...
- Hex colors: `&#RRGGBB` (1.16+ servers; older servers get the nearest classic color)
- Placeholders: `{prefix}` `{player}` `{status}` `{seconds}` `{time}` `{version}`
- PlaceholderAPI placeholders (`%...%`) work if PlaceholderAPI is installed
- An empty message (`''`) disables it

## Build (two jars)
GitHub Actions builds with JDK 17 and JDK 21:
- `MineStormStatus-1.0.0-java17.jar` - needs Java 17+ on the server
- `MineStormStatus-1.0.0-java21.jar` - needs Java 21+ on the server

Locally: `mvn clean package -Pjava17` or `mvn clean package -Pjava21`.
