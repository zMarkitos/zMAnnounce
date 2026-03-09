
---

# zMAnnounce

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.java.net/)
[![Velocity](https://img.shields.io/badge/Velocity-3.4.0-blue)](https://velocitypowered.com/)

A powerful and optimized announcement plugin for Velocity proxy servers. Supports global announcements, server-specific messages, and bossbar countdowns with full color support.

## Features

* Global & server-specific announcements
* Bossbar countdowns with customizable timers
* Full color support (Legacy, HEX, MiniMessage)
* Security features: rate limiting, message length limits
* Performance optimizations with caching
* Sound effects for announcements
* Title displays with customizable animations

## Installation

1. Download the latest release from the [releases page](https://github.com/zMarkitos/zMAnnounce/releases)
2. Place the `zMAnnounce-1.0.0.jar` file in your Velocity `plugins/` directory
3. Restart your Velocity server
4. Configure the plugin in `plugins/zMAnnounce/config.yml`

### Requirements

* Java 21 or higher
* Velocity 3.4.0 or higher
* Minecraft versions compatible with Velocity

## Configuration

After the first startup, a `config.yml` file is generated in `plugins/zMAnnounce/`.

Example snippet:

```yaml
announce-template:
  sound: "BLOCK_NOTE_BLOCK_PLING"
  title:
    enabled: true
    fadein: 10
    stay: 70
    fadeout: 20
    title: "&#FFC930&lANNOUNCEMENTS"
    subtitle: "&fCheck the chat!"
```

Supports legacy colors, HEX colors, and MiniMessage formatting.

## Commands

| Command                                       | Description          | Permission                |                      |
| --------------------------------------------- | -------------------- | ------------------------- | -------------------- |
| `/zmannounce alert <message> <server          | all>`                | Send an announcement      | `zmannounce.use`     |
| `/zmannounce bossbar <message> <time> <server | all>`                | Start a bossbar countdown | `zmannounce.bossbar` |
| `/zmannounce reload`                          | Reload configuration | `zmannounce.reload`       |                      |

Time format examples: `10s`, `15m`, `1h`.

## Permissions

* `zmannounce.use` - Use alert commands (Default: OP)
* `zmannounce.bossbar` - Use bossbar commands (Default: OP)
* `zmannounce.reload` - Reload configuration (Default: OP)

## Security & Performance

* Rate limiting and input validation
* Maximum message and bossbar limits
* Color caching and thread-safe operations

## 🔄 Update Checker

zMAnnounce automatically checks for updates on startup and displays a notification in the console when a new version is available.

### Configuration

```yaml
updates:
  # Enable automatic update checking
  # Set to false to disable version checks
  check-for-updates: true

  # Notify players when they join if a new version is available
  # Shows a chat message and title notification
  notify-players-on-join: true
```

### Features

- **Automatic Updates**: Checks Modrinth API for new versions on startup
- **Player Notifications**: Optional notifications for players when they join
- **Configurable**: Can be disabled in config.yml
- **Non-intrusive**: Only logs to console, doesn't affect server performance
- **Async**: Update checks run in background without blocking server startup

## 📄 License

## Support

* Issues: [GitHub Issues](https://github.com/zMarkitos/zMAnnounce/issues)
* Discord: [Link Discord](https://discord.com/invite/R7PNG2fChs)


---

