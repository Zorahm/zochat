# zoChat

![GitHub](https://img.shields.io/github/license/Zorahm/zoChat) ![GitHub Release](https://img.shields.io/github/v/release/Zorahm/zoChat)

A Paper plugin that adds local/global chat, private messaging, player mentions, timed announcements, bubble chat and chat logging to Minecraft servers. Integrates with LuckPerms for prefix/suffix display and uses the Adventure API (MiniMessage) for rich text formatting.

---

## Features

- **Local and global chat**
  - Local chat (default) — messages visible within a configurable radius.
  - Global chat (prefix `!` or `/g`) — messages visible to all players.
  - Commands `/l` and `/g` for explicit channel selection.
- **Player mentions**
  - `@PlayerName` with partial name matching, max 5 per message.
  - `@everyone` / `@here` with permission checks and radius limits.
  - Sound and action bar notifications for mentioned players.
- **Private messages**
  - `/msg` and `/reply`, with offline delivery on next join.
- **Anti-spam and filtering**
  - Per-channel cooldowns (local / global / private, configured in seconds).
  - Banned words filter with `exact`, `contains` and `smart` modes, `block` or `replace` action, plus `regex:` entries.
- **In-chat placeholders**
  - `^loc`, `^world`, `^time`, `^health`, `^ping`, `^biome`, `^item` with aliases, per-placeholder permissions and custom PlaceholderAPI-backed entries — all configured in `placeholders.yml`.
- **PlaceholderAPI integration** (optional)
  - Expands `%...%` in chat formats, and optionally in player-typed messages (opt-in).
  - Registers its own expansion: `%zochat_version%`, `%zochat_local_radius%`, `%zochat_here_radius%`, `%zochat_cooldown_local%`, `%zochat_cooldown_global%`.
- **Announcer**
  - Timed broadcasts on independent timers, sequential or random rotation, configured in `announcer.yml`.
- **Bubble chat**
  - Floating `TextDisplay` above the player's head on chat and/or `/bubble`, configured in `bubble.yml`.
- **Command guard**
  - Blocks console-only commands (`/op`, `/stop`, `/seed`, …) for players, including `minecraft:`-namespaced bypasses, configured in `commands.yml`.
- **`/say` formatting**
  - Styles vanilla `/say` output like the rest of the chat, for both players and console.
- **LuckPerms integration**
  - Prefixes and suffixes are rendered from legacy colour codes *and* MiniMessage tags, so an open colour in a suffix carries into the message.
- **Chat logging**
  - SQLite (default) or MySQL backend, all writes async.
- **Localization**
  - Russian and English message files; extensible via `messages_<lang>.yml`.

---

## Requirements

- Paper 1.21.10 (or a compatible fork)
- Java 21
- LuckPerms 5.4+ (required)
- PlaceholderAPI (optional)

---

## Installation

1. Download the latest release from [GitHub Releases](https://github.com/Zorahm/zoChat/releases).
2. Place `zoChat-2.0.0.jar` into your server's `plugins/` directory.
3. Restart the server.
4. Configure via the files in `plugins/zoChat/` (see below), then run `/chat reload`.

---

## Commands

| Command | Aliases | Description | Permission |
|---|---|---|---|
| `/chat reload` | `/zochat` | Reload configuration and messages | `zochat.admin` |
| `/chat help` | `/zochat` | Show command reference | all players |
| `/msg <player> <message>` | `/m`, `/tell`, `/w` | Send a private message | `zochat.msg` |
| `/reply <message>` | `/r` | Reply to the last private message | `zochat.msg` |
| `/local <message>` | `/l` | Send a message to local chat | `zochat.local` |
| `/global <message>` | `/g` | Send a message to global chat | `zochat.global` |
| `/bubble <message>` | `/b` | Show a bubble above your head | `zochat.bubble` |
| `/chatlog <player\|clear>` | — | View or clear chat logs | `zochat.admin` |

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `zochat.admin` | Admin commands (`/chat reload`, `/chatlog`) | op |
| `zochat.local` | Use local chat | true |
| `zochat.global` | Use global chat | true |
| `zochat.msg` | Send private messages | true |
| `zochat.bubble` | Use `/bubble` | true |
| `zochat.spam.bypass` | Bypass anti-spam cooldowns | op |
| `zochat.command.bypass` | Run commands blocked by the command guard | op |
| `zochat.stealth.join` | Hide join message | op |
| `zochat.stealth.quit` | Hide quit message | op |
| `zochat.mention.everyone` | Use `@everyone` | op |
| `zochat.mention.here` | Use `@here` | op |
| `zochat.placeholder.loc` (also `.world`, `.time`, `.health`, `.ping`, `.biome`, `.item`) | Use the matching `^` placeholder | true |
| `zochat.placeholder.papi` | Expand `%...%` in your own messages | op |

---

## Configuration

| File | Contents |
|---|---|
| `config.yml` | Chat formats, cooldowns, banned words, mentions, `/say`, database, join/quit, PlaceholderAPI |
| `placeholders.yml` | `^` placeholders — built-in, custom (PlaceholderAPI-backed), world-name translation |
| `announcer.yml` | Announcers and announcement bodies |
| `bubble.yml` | Bubble chat (TextDisplay) settings |
| `commands.yml` | Console-only command list for the command guard |
| `messages/messages_ru.yml`, `messages/messages_en.yml` | Player-facing strings; language selected by `message:` in `config.yml` |
| `welcome_messages/welcome_messages.yml` | Welcome messages shown on join |

New keys added by plugin updates are merged into your existing files without overwriting your values.

### `config.yml` (excerpt)

```yaml
# Language: ru or en
message: ru

anti-spam:
  enabled: true
  local-cooldown: 3        # seconds
  global-cooldown: 5       # seconds
  private-cooldown: 2      # seconds
  bypass-permission: "zochat.spam.bypass"

banned-words:
  enabled: true
  mode: "smart"            # exact | contains | smart
  action: "block"          # block | replace
  normalize: true          # catch l33t / spaced-out / look-alike bypasses
  words: []                # e.g. ["badword", "regex:\\b\\d{4}\\b"]

local-chat:
  enabled: true
  radius: 50
  format: "<gradient:#55ff55:#aaffaa>[Local]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> > </#c0c0c0>{message}"

global-chat:
  enabled: true
  format: "<gradient:#ffaa33:#ffd700>[Global]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> > </#c0c0c0>{message}"

say:
  enabled: true
  console-name: "Server"
  format: "<gradient:#ffaa33:#ffd700>[{player}]</gradient> <#c0c0c0>></#c0c0c0> <white>{message}</white>"

private-messages:
  format: "<gradient:#f6a0d3:#b47ee5>PM from {player}:</gradient> <white>{message}</white>"
  reply-format: "<gradient:#b47ee5:#f6a0d3>You -> {player}:</gradient> <white>{message}</white>"

database:
  type: "sqlite"           # sqlite | mysql
  mysql:
    host: "localhost"
    port: 3306
    database: "minecraft_chat"
    username: "root"
    password: "password"

placeholder-api:
  enabled: true
  format: true             # expand %...% in chat formats (admin-controlled)
  player-messages: false   # expand %...% players type themselves (opt-in, abuse risk)
  player-permission: "zochat.placeholder.papi"
```

Chat formats use MiniMessage: HEX colours (`<#ff5555>`), gradients (`<gradient:#55ff55:#aaffaa>`) and the tokens `{prefix}`, `{suffix}`, `{player}`, `{message}`. Text players type is escaped, so nobody can inject colours or clickable commands through chat.

### `messages/messages_en.yml` (excerpt)

```yaml
errors:
  only-players: "<red>This command is for players only!</red>"
  no-permission: "<red>You do not have permission to do that!</red>"

chat:
  spam-warning: "<red>Don't spam! Wait a bit before sending another message.</red>"
  banned-word: "<red>Message contains banned words!</red>"
  local-disabled: "<red>Local chat is disabled on the server!</red>"
```

---

## Building from source

```bash
./gradlew clean build
```

Output: `build/libs/zoChat-2.0.0.jar`

Run the test suite:

```bash
./gradlew test
```

---

## License

See [LICENSE](LICENSE).
