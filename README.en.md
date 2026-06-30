# AchievementCheck

A Paper plugin that lets **each player check, for themselves, what is still missing** from vanilla advancements (server-side only).

No more "I'm almost done with Adventuring Time (visit every biome), but **I have no idea which biomes I still haven't visited**." Open the menu with `/ac`, hover an advancement icon, and the **specific missing biomes / criteria** are shown in the tooltip. Click an icon to page through every criterion.

> **No client mod required.** Just install the plugin on the server — vanilla clients work as-is.

## The problem it solves

Multi-criteria vanilla advancements such as "Adventuring Time" (all biomes), "A Balanced Diet" (all foods), and "Monsters Hunted" (all hostile mobs) store their progress on the server, but the in-game advancement screen **doesn't tell you which specific items remain**.
AchievementCheck reads each player's advancement progress and shows the **completed / remaining criteria one by one**, so it's obvious where to go, what to eat, or what to kill next.

## Features

- **GUI menu (primary)**: `/ac` opens a chest-style menu listing the "collection" advancements. Hover an icon to see the progress (`達成 n / m`) and the **remaining criteria** in the tooltip.
- **Detail view**: click an icon in the menu to page through **all criteria** of that advancement. Missing ones come first; completed ones are shown in green with a glint.
- **Chat output (secondary)**: `/ac <name>` lists the remaining and completed criteria in chat.
- **Automatic localization**: advancement titles and biome / item / mob names are sent as translatable components, so they appear **in each player's client language** (e.g. Japanese). When a key can't be translated, a humanized key name is shown as a fallback.
- **OR advancements are excluded**: advancements completed by satisfying *any one* of several criteria don't fit the "what's missing" concept, so they're excluded from the list.

## Requirements

- Server: Paper 26.2 (experimental channel)
- Java: 25
- Clients: vanilla (no mods, server-side only)

## Installation

1. Drop `AchievementCheck-1.0.0.jar` into `plugins/` and restart.
2. From then on, each player can check their advancement progress with `/ac`. No configuration needed.

## Usage

Installing it is enough to use `/ac`. To change behavior, use `config.yml` (below) or `/ac reload`.

- Open the menu to see what's missing → `/ac`
- Check a specific advancement in chat → `/ac <name>` (e.g. `/ac adventuring_time`; tab completion works)
- Apply changes you made to `config.yml` → `/ac reload`

GUI controls:

- **Hover** an icon → progress and remaining criteria appear in the tooltip.
- **Click** an icon → page through all criteria of that advancement.
- Bottom row arrows page through; `一覧へ戻る` returns to the list; `閉じる` closes.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/ac` | Open the advancement-check menu (GUI) | `achievementcheck.use` |
| `/ac <name>` | Show remaining / completed criteria of an advancement in chat | `achievementcheck.use` |
| `/ac reload` | Reload the configuration | `achievementcheck.manage` |

Alias: `/achievementcheck`

## Permissions

| Permission node | Description | Default |
|---|---|---|
| `achievementcheck.use` | Self-facing `/ac` (GUI and detail view) | `true` (everyone) |
| `achievementcheck.manage` | Server-wide operations such as `reload` | `op` |

## Configuration (`config.yml`)

```yaml
enabled: true            # Enable the plugin (while false, /ac checks are disabled; apply with reload)
show-completed: true     # Also show completed criteria in detail views (false = missing only)
hover-missing-limit: 10  # Max number of "missing" criteria shown in the GUI tooltip (rest summarized)
max-list: 80             # Max criteria listed per section in /ac <name> chat output (rest summarized)
gui-rows: 6              # GUI chest rows; the bottom row is navigation. 2-6 (default 6)
```

## How it works / technical notes

- Enumerates all advancements via `Server#advancementIterator()` and keeps only **AND advancements with multiple criteria** (where listing what's missing makes sense). OR advancements, recipe unlocks (`recipes/`), and hidden advancements are excluded.
- For each player, reads `getAwardedCriteria()` / `getRemainingCriteria()` from `Player#getAdvancementProgress(advancement)`.
- Criterion keys (e.g. `minecraft:plains`) are resolved as mob / item / biome and converted to `Component.translatable(...)` so they display in the **client's language** (humanized key as fallback).
- The GUI is a chest inventory backed by an `InventoryHolder`; all clicks are **cancelled** (read-only) and only navigation is handled.

### Limitations

- Only the **online player themselves** can be checked (`getAdvancementProgress` requires an online Player). Checking other or offline players is not supported.
- **The vanilla advancement screen (the L key) cannot be modified**: its description text is fixed in the advancement definition and the API is read-only. This plugin supplements it with a custom GUI instead.
- Localization of criterion names depends on the client's language setting. On clients that can't resolve the translation keys, the humanized key name is shown.
- Paper 26.2 is experimental (alpha). The API may change and require updates.

## Build

```bash
./deploy.sh        # Local build (JDK 25 + Maven). Output: target/AchievementCheck-1.0.0.jar
# or
mvn -B clean package
```

Pushing a `v*` tag triggers GitHub Actions (`.github/workflows/build.yml`) to build and attach the jar to the release.

## Deploying to a server

Place the jar in the server's `plugins/` and restart. Get the jar one of two ways (A or B). With Docker (itzg/minecraft-server), you can also use the auto-download approach below.

### A. Use a release build (no build required, recommended)

Download the latest `AchievementCheck-<version>.jar` from [Releases](https://github.com/astail/mc-achievement-check/releases). No JDK or Maven needed.

```bash
gh release download --repo astail/mc-achievement-check --pattern '*.jar'
```

### B. Build it yourself

Follow [Build](#build) to produce `target/AchievementCheck-1.0.0.jar`.

### Docker Compose (itzg/minecraft-server) auto-download

```yaml
services:
  mc:
    image: itzg/minecraft-server
    tty: true
    stdin_open: true
    ports:
      - "25565:25565"
    environment:
      EULA: "TRUE"
      TYPE: "PAPER"
      VERSION: "26.2"
      PAPER_CHANNEL: "experimental"
      PLUGINS: |
        https://github.com/astail/mc-achievement-check/releases/download/v1.0.0/AchievementCheck-1.0.0.jar
    volumes:
      - ./data:/data
    restart: unless-stopped
```

You'll see this in the startup log on success:

```text
[AchievementCheck] AchievementCheck を有効化しました（状態: ON / 達成済み表示: あり）。
```

## License

MIT License — see [LICENSE](LICENSE).
