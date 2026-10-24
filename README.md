# zoChat

![License](https://img.shields.io/github/license/Zorahm/zoChat) ![Release](https://img.shields.io/github/v/release/Zorahm/zoChat)

zoChat is a chat plugin for Paper servers. It splits chat into local and global channels, adds private messages with offline delivery, mentions, in-chat placeholders, timed announcements, chat bubbles above players' heads and a chat log. Formatting uses MiniMessage throughout, and LuckPerms prefixes and suffixes work whether they are written with legacy colour codes or MiniMessage tags.

## Requirements

- Paper 1.21.10 or a compatible fork
- Java 21
- LuckPerms 5.4 or newer (required)
- PlaceholderAPI (optional)
- DiscordSRV (optional)

## Installation

1. Download `zoChat-<version>.jar` from [GitHub Releases](https://github.com/Zorahm/zoChat/releases).
2. Put it into the server's `plugins/` folder and restart the server.
3. Edit the files in `plugins/zoChat/` and apply the changes with `/chat reload`.

When an update adds new settings, they are merged into your existing files; your values are kept.

## Features

### Local and global chat

Plain chat goes to players within `local-chat.radius` blocks. A message that starts with `!` goes to the whole server. `/l` and `/g` pick the channel explicitly. If local chat is disabled, plain chat goes global; if both channels are disabled, zoChat leaves chat to vanilla.

`/g`, `/l` and `/bubble` are sent through the regular chat event, so mute, anti-spam and chat-logging plugins treat them exactly like typed chat.

### Mentions

- `@Name` mentions a player; a unique prefix of the name is enough. Up to 5 players per message.
- `@everyone` (aliases `@all`, `@все`) and `@here` (alias `@здесь`, players within `mention.here-radius`) need their own permissions.
- Mentioned players hear a sound and get an action-bar notice. Only players who actually receive the message are notified, so a mention in local chat does not reach the other end of the map.
- Vanished players cannot be mentioned or tab-completed.

### Private messages

`/msg` and `/reply`. A message to an offline player is stored and delivered when they join; `/reply` to someone who has left goes the same way. Vanished players are treated as offline.

### Anti-spam and word filter

- Separate cooldowns for local chat, global chat, private messages and `/bubble`, in seconds.
- The banned-words filter has three modes (`exact`, `contains`, `smart`) and two actions (`block`, `replace`), and accepts `regex:` entries. Normalization catches common bypasses: spaced-out letters, l33t digits, Cyrillic look-alikes and stretched letters (`baaad`). In `smart` mode a word only matches on word boundaries, so `ass` does not fire inside `class`.

### In-chat placeholders

Players can drop `^loc`, `^world`, `^time`, `^health`, `^ping`, `^biome` and `^item` into a message, each with aliases and its own permission. Custom `^` placeholders backed by PlaceholderAPI and display names for worlds are configured in `placeholders.yml`.

### PlaceholderAPI

- `%...%` placeholders are expanded in chat formats, announcements, bubbles and `/say`.
- Expanding placeholders that players type themselves is opt-in and permission-gated.
- zoChat registers its own placeholders: `%zochat_version%`, `%zochat_local_radius%`, `%zochat_here_radius%`, `%zochat_cooldown_local%`, `%zochat_cooldown_global%`.

### Announcer

Timed broadcasts with any number of announcers, each on its own interval, in sequential or random order, with optional sound, permission and world filters. Configured in `announcer.yml`.

### Chat bubbles

A floating text display above the player's head on chat, on `/bubble`, or both. Size, background, duration and visibility are set in `bubble.yml`.

### Command guard

Blocks console-only commands such as `/op`, `/stop` and `/seed` for every player, operators included. Namespaced forms (`/minecraft:op`) and commands wrapped in `/execute ... run` are caught too, and blocked commands are hidden from tab completion. Only players with `zochat.command.bypass` can run them; nobody has it by default. Note that the LuckPerms `*` wildcard grants it as well. The list is in `commands.yml`.

### /say formatting

Styles vanilla `/say` like the rest of the chat. Players still need the vanilla `minecraft.command.say` permission. Text from players is shown as plain text; text from the console and command blocks may use MiniMessage.

`say.console-format` gives the console, command blocks and RCON a format of their own; leave it blank to share `say.format` with players. For example `"{prefix}<#c0c0c0>›</#c0c0c0> <white>{message}</white>"` shows only the console badge, without the console name.

### Rank badges

Pixel-art rank badges (OWNER, ADMIN, MOD, ..., CONSOLE) are shipped as glyphs of a server resource pack, so they look the same at every GUI scale. Put `<badge:admin>` into a LuckPerms prefix or suffix, or into the chat, `/say` and private-message formats; `<badge:admin:ru-icon>` picks a variant (`en`, `en-icon`, `ru`, `ru-icon`), otherwise `badges.language` and `badges.icons` in `config.yml` decide. Until `badges.enabled` is turned on, and always in the server console, a badge is shown as a text label such as `[ADMIN]`. The pack is built from this repository, see [Rank badge resource pack](#rank-badge-resource-pack).

### Console prefix

The console has its own prefix and name, shown in `/say` and in private messages. By default the prefix is the CONSOLE badge. The console can `/msg` players, and players can answer it with `/reply`. Change the prefix with `/chat consoleprefix <prefix>` or in `config.yml`. Leave `console.name` blank to show only the prefix in private messages.

### DiscordSRV

With [DiscordSRV](https://modrinth.com/plugin/discordsrv) installed, global chat goes to Discord, and local chat can too (`discordsrv.local-chat`, off by default), into the same Discord channel or a separate one. Messages from Discord are shown in game with zoChat's own format (`formats.discord` in the messages file): the top Discord role in its colour, the name, the text and attachments as clickable links. Discord users get the same banned-words filter as players. Keep `RespectChatPlugins: true` in DiscordSRV's config, otherwise global chat reaches Discord twice.

### Join, quit and welcome messages

Optional custom join and quit messages with sounds, plus a delayed welcome message for the joining player. With the custom messages turned off, vanilla join and quit messages are left as they are.

### Chat log

Every chat message is stored in SQLite (default) or MySQL. All database work runs off the main thread. `/chatlog <player>` shows a player's latest messages.

### Localization

Every text players see, including the chat, private-message and join/quit formats, lives in `messages/messages_ru.yml` and `messages/messages_en.yml`. Pick the language with `message:` in `config.yml`.

## Commands

| Command | Aliases | Description | Permission |
|---|---|---|---|
| `/chat help` | `/zochat` | Command reference | none |
| `/chat reload` | `/zochat` | Reload all configuration files | `zochat.admin` |
| `/chat consoleprefix [prefix\|clear]` | `/zochat` | Show, set or remove the console prefix | `zochat.admin` |
| `/global <message>` | `/g` | Send to global chat | `zochat.global` |
| `/local <message>` | `/l` | Send to local chat | `zochat.local` |
| `/msg <player> <message>` | `/m`, `/tell`, `/w` | Private message (also from the console) | `zochat.msg` |
| `/reply <message>` | `/r` | Reply to the last private message (also to or from the console) | `zochat.msg` |
| `/bubble <message>` | `/b` | Show a bubble above your head | `zochat.bubble` |
| `/chatlog <player>` | | Show a player's recent messages | `zochat.admin` |
| `/chatlog clear` | | Delete the whole chat log | `zochat.admin` |

## Permissions

| Permission | Grants | Default |
|---|---|---|
| `zochat.admin` | `/chat reload`, `/chatlog` | op |
| `zochat.local` | Local chat (plain chat and `/local`) | everyone |
| `zochat.global` | Global chat (`!` prefix and `/global`) | everyone |
| `zochat.msg` | `/msg` and `/reply` | everyone |
| `zochat.bubble` | `/bubble` | everyone |
| `zochat.spam.bypass` | Skip anti-spam cooldowns | op |
| `zochat.command.bypass` | Run commands blocked by the command guard | nobody |
| `zochat.stealth.join` | Join without a join message | op |
| `zochat.stealth.quit` | Leave without a quit message | op |
| `zochat.mention.everyone` | `@everyone` | op |
| `zochat.mention.here` | `@here` | op |
| `zochat.placeholder.<name>` | The matching `^` placeholder (`loc`, `world`, `time`, `health`, `ping`, `biome`, `item`) | everyone |
| `zochat.placeholder.papi` | Expand `%...%` in your own messages | op |

## Configuration files

| File | Contents |
|---|---|
| `config.yml` | Language, cooldowns, banned words, mentions, channels, `/say`, console, badges, DiscordSRV, database, join/quit, PlaceholderAPI |
| `messages/messages_<lang>.yml` | Every player-facing text, including chat, private-message and join/quit formats |
| `placeholders.yml` | `^` placeholders and world display names |
| `announcer.yml` | Announcers and announcement texts |
| `bubble.yml` | Chat bubble settings |
| `commands.yml` | Commands blocked by the command guard |
| `welcome_messages/welcome_messages.yml` | Welcome message for joining players |

### Formats

Formats are MiniMessage strings: HEX colours (`<#ff5555>`), gradients (`<gradient:#55ff55:#aaffaa>`) and the tokens `{prefix}`, `{suffix}`, `{player}` and `{message}`. From `messages_en.yml`:

```yaml
formats:
  local-chat: "<gradient:#55ff55:#aaffaa>[Local]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}"
  global-chat: "<gradient:#ffaa33:#ffd700>[Global]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}"
```

The prefix and suffix are inserted without being closed, so a colour left open in a suffix carries over into the message. Everything players type is escaped: they cannot add colours, hover text or clickable commands to chat, private messages, bubbles or item names shown with `^item`.

### config.yml (excerpt)

```yaml
message: en                  # ru or en

anti-spam:
  enabled: true
  local-cooldown: 3          # seconds
  global-cooldown: 5
  private-cooldown: 2
  bubble-cooldown: 3

banned-words:
  enabled: true
  mode: "smart"              # exact | contains | smart
  action: "block"            # block | replace
  normalize: true
  words: []                  # e.g. ["badword", "regex:\\b\\d{4}-\\d{4}\\b"]

local-chat:
  enabled: true
  radius: 50

global-chat:
  enabled: true

console:
  prefix: "<badge:console> "  # set in game with /chat consoleprefix
  name: "Server"

badges:
  enabled: false             # turn on once players get the resource pack
  language: auto             # auto (follows message:), en or ru
  icons: true

discordsrv:
  enabled: true
  global-channel: "global"   # DiscordSRV game channels (Channels: in its config.yml)
  local-chat: false          # also send local chat to Discord
  local-channel: "global"
  discord-format: true       # zoChat formats Discord messages (formats.discord)
  max-length: 256

database:
  type: "sqlite"             # sqlite | mysql
  mysql:
    host: "localhost"
    port: 3306
    database: "minecraft_chat"
    username: "root"
    password: "password"
```

## Working with other plugins

- Mute plugins: chat, `/g`, `/l` and `/bubble` all pass through the chat event, so a mute applies to all of them. `/msg` and `/reply` (with the aliases `/m`, `/tell`, `/w`, `/r`) are commands; add them to your mute plugin's list of blocked commands if it does not include them already.
- Command conflicts: if another plugin, such as EssentialsX, already owns `/msg`, `/tell`, `/w`, `/m`, `/reply` or `/r`, the server sends the command to that plugin, and replies stop working with zoChat. Disable those commands in the other plugin (EssentialsX: `disabled-commands`) or use the prefixed forms `/zochat:msg` and `/zochat:r`.
- PlaceholderAPI values used in formats are parsed as MiniMessage. Do not put placeholders that return player-typed text, such as nicknames, into formats.

## Rank badge resource pack

The badges are drawn by the generator in `tools/prefixgen`. It writes the resource pack and the character table the plugin reads (`src/main/resources/badges.yml`), so the plugin and a pack built from the same commit always match.

```bash
./gradlew :prefixgen:run
```

It writes one pack per style (`plate`, `neon`, `craft`, `ribbon`) to `tools/prefixgen/build/resourcepack/zochat-badges-<style>.zip` and prints the SHA-1 of each for `resource-pack-sha1` in `server.properties`. The packs are interchangeable, so serve whichever style you like; preview sheets of all of them are written to `tools/prefixgen/build/preview/`. Every GitHub release also carries the four packs next to the jar. Once a pack is served to players, set `badges.enabled: true`.

Each badge keeps a fixed character forever, so adding ranks or switching styles never breaks prefixes that are already in use.

## Building from source

```bash
./gradlew clean build
```

The plugin jar is written to `build/libs/zoChat-<version>.jar`. To run only the tests:

```bash
./gradlew test
```

## License

See [LICENSE](LICENSE).
