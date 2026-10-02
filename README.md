# MineStormStatus

Paper/Purpur plugin by Muvixo: player statuses, auto-AFK and a persistent action bar HUD.

## Commands
| Command | Description | Permission |
|---|---|---|
| `/status busy\|idle\|away\|clear` | Set / clear your status | `minestormstatus.use` |
| `/minestormstatus` (`/mss`) | Help | `minestormstatus.command` |
| `/minestormstatus busy\|idle\|away\|clear` | Same as `/status` | `minestormstatus.use` |
| `/minestormstatus creator` | Shows the creator | `minestormstatus.command` |
| `/minestormstatus info` | Plugin version | `minestormstatus.command` |
| `/minestormstatus reload` | Reload config.yml + messages.yml | `minestormstatus.reload` |

Other permission: `minestormstatus.afk.bypass` (exempt from auto-AFK).

## Files
- `config.yml` - behaviour (AFK timeout, intervals, mention alerts, which activity resets AFK).
- `messages.yml` - every message, the prefix and the action bar HUD (MiniMessage format).

## Build (two jars)
GitHub Actions builds with JDK 17 and JDK 21:
- `MineStormStatus-1.0.0-java17.jar` - compiled against Paper 1.20.4 API (servers on Java 17: 1.20 - 1.20.4).
- `MineStormStatus-1.0.0-java21.jar` - compiled against Paper 1.21.4 API (servers on Java 21: 1.20.5+).

Locally: `mvn clean package -Pjava17` or `mvn clean package -Pjava21`.
