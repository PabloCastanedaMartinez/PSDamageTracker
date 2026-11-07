# PSDamageTracker

**English** | [Español](README.es.md)

A Minecraft plugin that tracks player damage against **MythicMobs** mobs and displays a leaderboard at the end of each fight. Customize battle results, save statistics to SQLite, and access ranking positions through PlaceholderAPI.

[Installation](#installation) · [Configuration](#configuration) · [Commands](#commands-and-permissions) · [Placeholders](#placeholderapi) · [Building](#building)

## Features

- Tracks melee damage and projectiles fired by players.
- Tracks each entity independently, including multiple mobs with the same internal ID.
- Ranks players by damage, with their position and contribution percentage.
- Supports server-wide or participant-only victory messages, personal results, and messages for non-participants.
- Customizable templates with MiniMessage and legacy `&` color codes.
- Optional SQLite storage for cumulative statistics per player and boss type.
- Optional PlaceholderAPI and FancyHolograms integrations.

## Requirements

| Component | Requirement |
| --- | --- |
| Server | Built against Bukkit/Spigot API 1.19; declares `api-version: '1.19'`. |
| Java | Java 17 minimum for the plugin; use the version required by your server. |
| MythicMobs | Required. Built against API 5.4.1. |
| PlaceholderAPI | Optional, for player damage in active encounters and positions in the saved leaderboard. Built against API 2.11.6. |
| FancyHolograms | Optional, for temporary result holograms. Built against API 2.4.2. |

These dependency versions are build targets, rather than a verified compatibility matrix. Choose versions compatible with your server and test the combination before deploying it to production.

## Installation

1. Build the project using the [instructions below](#building).
2. Stop your server and install MythicMobs and any optional integrations you want to use.
3. Copy `build/libs/DamageTracker-2.2.0.jar` into the server's `plugins/` directory.
4. Start the server. The plugin creates its configuration in `plugins/PSDamageTracker/`.
5. Configure your MythicMobs IDs and message templates, then apply changes with `/damagetracker reload`.

Kotlin and the text formatting libraries are bundled in the Shadow JAR. You do not need a separate plugin to provide Kotlin.

## Configuration

The following files are generated on the first startup:

| File | Purpose |
| --- | --- |
| [`config.yml`](src/main/resources/config.yml) | Per-boss options, defaults, numeric formats, and persistence settings. |
| [`tracked_bosses.yml`](src/main/resources/tracked_bosses.yml) | Internal MythicMobs IDs to track. |
| [`victory_messages.yml`](src/main/resources/victory_messages.yml) | Victory, ranking, personal result, and non-participant message templates. |
| [`leaderboard_format.yml`](src/main/resources/leaderboard_format.yml) | General leaderboard line formatting. |

### Selecting bosses

Add case-sensitive internal mob IDs to `tracked_bosses.yml`:

```yaml
tracked_bosses:
  enabled_bosses:
    - SkeletonKing
    - RAID_BOSS
```

An empty list (`enabled_bosses: []`) tracks all MythicMobs mobs. This list filters tracking; message settings belong in `config.yml`.

### Customizing results

Add an entry under `bosses` in `config.yml`. For example:

```yaml
bosses:
  SkeletonKing:
    victory_message_id: DEFAULT_VICTORY
    top_players_to_show: 3
    position_format_id: DEFAULT
    broadcast_message: true
    personal_message_id: DEFAULT
    non_participant_message_id: DEFAULT
    hologram_enabled: false
```

Message and format IDs must match the corresponding sections in `victory_messages.yml`. Bosses without their own section use `default_boss_config`. Include all options in each custom boss section.

- `broadcast_message: true` broadcasts the victory message to the server.
- `broadcast_message: false` sends it to online participants.
- With `broadcast_message: false`, `hologram_enabled: true`, and FancyHolograms available, the victory message appears in a hologram above an online participant for five minutes.

Personal and non-participant messages are handled separately. Entries in `position_formats` override the general format from `leaderboard_format.yml` for positions that have a template.

### Template variables

These variables are used in message files and are independent of PlaceholderAPI.

| Context | Variables |
| --- | --- |
| Victory message | `{boss_name}`, `{top_players}` |
| Position format | `{position}`, `{player_name}`, `{damage}`, `{percentage}` |
| Personal message | `{boss_name}`, `{player_name}`, `{position}`, `{damage}`, `{percentage}` |
| Non-participant message | `{boss_name}` |
| General leaderboard format | `{symbol}`, `{position}`, `{name}`, `{damage}`, `{percentage}` |

Example template in `victory_messages.yml`:

```yaml
messages:
  DEFAULT_VICTORY: |
    <gold>{boss_name} has been defeated!</gold>
    <yellow>Top contributors:</yellow>
    {top_players}
```

### Saving statistics

Persistence is disabled by default. To save results when a boss dies, update this setting in `config.yml`:

```yaml
tracking_config:
  persist_data: true
```

Results accumulate by player UUID and boss ID in `plugins/PSDamageTracker/damage_tracker.db`. No external database server is required. Without persistence, encounter data is removed from memory when the mob dies and is not added to the PlaceholderAPI leaderboard.

## Commands and permissions

Use `/dtr` as an alias for `/damagetracker`.

| Command | Description | Permission | Default access |
| --- | --- | --- | --- |
| `/damagetracker` | Displays help. | None | Everyone |
| `/damagetracker check <bossID>` | Shows the top three players across active encounters for that ID. | `damageTracker.check` | Everyone |
| `/damagetracker reload` | Reloads configuration and message templates. | `damageTracker.reload` | Operators |

`check` combines damage from active entities sharing the same boss ID. Once an encounter ends, its data no longer appears in this command. Use PlaceholderAPI to access saved rankings.

## PlaceholderAPI

The `dt` expansion registers automatically when PlaceholderAPI is installed. No eCloud expansion download is required.

| Placeholder | Result |
| --- | --- |
| `%dt_damage%` | Total damage dealt by the current player, identified by UUID, across all active tracked encounters. |
| `%dt_damagetop_<bossID>_<position>%` | Player name at that position in the saved cumulative leaderboard. Positions start at `1`. |

`%dt_damage%` requires player context and returns `0.0` when no player is provided or the player has no damage in active encounters. An encounter's contribution is removed from the total when it finishes or is discarded. This placeholder works without persistence and excludes historical SQLite totals. It returns the numeric value directly, without applying `damage_format`.

For example, `%dt_damagetop_SkeletonKing_1%` returns the name of the player with the highest cumulative damage against `SkeletonKing`. Boss IDs may contain underscores.

Enable `tracking_config.persist_data` to record new results for `%dt_damagetop_<bossID>_<position>%`. This ranking placeholder returns `No player found` when no player occupies the requested position, or `Invalid position` when the position is invalid.

## Building

You need **JDK 17** available for the toolchain. The repository includes Gradle Wrapper, so a separate Gradle installation is unnecessary. The first run needs an internet connection to download Gradle and dependencies.

From the project root on Linux or macOS:

```sh
./gradlew clean build
```

On Windows with PowerShell:

```powershell
.\gradlew.bat clean build
```

The `build` task runs the tests and produces a JAR with bundled dependencies through `shadowJar`. For the current version, install `build/libs/DamageTracker-2.2.0.jar`.

To run only the tests:

```sh
./gradlew test
```

In PowerShell, use `.\gradlew.bat test`. Test reports are written to `build/reports/tests/test/index.html`.

Unit tests for damage tracking and persistence are in [`src/test/kotlin`](src/test/kotlin). The [`integration/run-paper.ps1`](integration/run-paper.ps1) script can check an existing local test server. Adjust its Java path and prepare Paper and the required plugins in `integration/paper-<version>/` before running it.

## Reporting issues and contributing

Include your Minecraft version, server software, Java version, PSDamageTracker version, and installed dependency versions, along with reproduction steps and relevant console messages. For configuration-related issues, provide a minimal example.

When contributing, follow the existing code style and run the tests before proposing changes.
