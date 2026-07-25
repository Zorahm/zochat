# AGENTS.md

This file provides guidance to AI coding assistants when working with code in this repository.

## Project Overview

zoChat is a Minecraft Paper plugin (Java 21, Paper API 1.21.10) that provides enhanced chat functionality including local/global chat, private messaging, player mentions, and chat logging. The plugin uses LuckPerms for prefix/suffix display and Adventure API (MiniMessage) for rich text formatting with HEX colors and gradients.

## Build & Development Commands

```bash
# Build plugin JAR
./gradlew clean build

# Compiled JAR location
build/libs/zoChat-2.0.0.jar

# Run test server (requires Minecraft assets downloaded by run-paper)
./gradlew runServer

# Run tests only
./gradlew test

# Java 21 toolchain, Shadow plugin bundles MySQL driver into final JAR
```

## Architecture

### Core Plugin Lifecycle (ZoChatPlugin.java)

Main entry point at `src/main/java/zorahm/zochat/ZoChatPlugin.java`. On enable:
1. Displays ASCII banner using Adventure Components
2. Checks for LuckPerms dependency (required)
3. Initializes: ChatConfig, Messages (ru/en), Database (SQLite/MySQL), repositories
4. Creates services: BannedWordsFilter, MentionHandler, PlaceholderService, ChatService, PrivateMessageService, WelcomeMessages
5. Registers commands: `/chat`, `/global` (`/g`), `/local` (`/l`), `/msg`, `/reply` (`/r`), `/chatlog`
6. Registers event listeners: ChatListener, PresenceListener

### Package Layout

```
zorahm.zochat
├── ZoChatPlugin.java                 // lifecycle + manual DI wiring only
├── config/
│   ├── ChatConfig.java               // typed getters over config.yml
│   ├── Messages.java                 // ru/en player-facing strings
│   └── ConfigFiles.java              // saveDefault + non-destructive merge of missing keys
├── chat/
│   ├── ChatService.java              // THE single send pipeline (local + global + /g + /l)
│   ├── ChatChannel.java              // enum LOCAL/GLOBAL
│   ├── ChatListener.java             // AsyncChatEvent -> ChatService
│   ├── GlobalCommand.java            // /g -> ChatService.send(GLOBAL)
│   ├── LocalCommand.java             // /l -> ChatService.send(LOCAL)
│   ├── PrefixFormatter.java          // LuckPerms legacy -> Component
│   ├── MentionHandler.java           // @player/@everyone/@here
│   ├── MentionTabCompleter.java      // tab completion for @mentions
│   ├── BannedWordsFilter.kt          // exact/contains/smart filter + position-mapped normalization (Kotlin)
│   ├── CooldownService.kt            // per-channel anti-spam cooldowns (ConcurrentHashMap)
│   ├── PlaceholderConfig.kt          // loads placeholders.yml (builtin/custom ^ defs + world-names)
│   └── PlaceholderService.java       // ^loc/^world/custom; regex match; escapes player input
├── announcer/
│   ├── AnnouncerConfig.kt            // loads announcer.yml (announcers + announcements-by-ID)
│   ├── AnnouncerService.kt           // one repeating main-thread task per announcer (staggered), per-player send with per-line parse cache
│   ├── AnnouncementSelector.kt       // pure SEQUENTIAL/RANDOM rotation (unit-tested)
│   └── SelectionType.kt              // SEQUENTIAL | RANDOM (fallback SEQUENTIAL)
├── bubble/
│   ├── BubbleConfig.kt               // loads bubble.yml (core TextDisplay settings)
│   ├── BubbleService.kt              // TextDisplay above head; 1-tick task follows + expires it
│   ├── BubbleCommand.kt              // /bubble (/b) <text>
│   └── TriggerType.kt                // CHAT | COMMAND | CHAT_COMMAND (fallback CHAT)
├── privatemsg/
│   ├── PrivateMessageService.java    // /msg + /reply core, offline hand-off
│   ├── MsgCommand.java
│   └── ReplyCommand.java
├── presence/
│   ├── PresenceListener.java         // join + quit + welcome + offline delivery
│   └── WelcomeMessages.java          // welcome_messages.yml loader
├── storage/
│   ├── Database.java                 // single connection, async writes via single-thread executor
│   ├── ChatLogRepository.java        // chat_logs table
│   └── OfflineMessageRepository.java // offline_messages table
├── command/
│   ├── ChatCommand.java              // /chat reload|help
│   └── ChatLogCommand.java           // /chatlog <player|clear>
└── util/
    ├── Sounds.kt                     // safe Sound.valueOf -> Optional<Sound>
    └── ClassPreloader.kt             // eager class load at onEnable — lazy loads read the shaded jar on the main thread mid-tick (watchdog stall on slow-I/O hosts)
```

### Chat Flow (ChatService.java)

All chat messages (natural chat, /g, /l) go through a single `ChatService.send(player, rawMessage, channel)`. Natural chat arrives on `AsyncChatEvent` (off-thread); `ChatListener` cancels the event and re-dispatches `send` to the main thread, because the pipeline reads player state (inventory/health/location) and plays sounds. The order:
1. Banned words filter (block or replace mode) — before the cooldown so a rejected message doesn't burn it
2. Anti-spam cooldown check (per-channel via `CooldownService`; bypass permission: `zochat.spam.bypass`)
3. The player's text is **escaped once** (`miniMessage.escapeTags`) — player-typed `<...>` is never parsed as MiniMessage (no colour/`<click:run_command>` injection). Everything below works on that escaped string; there is no Component→serialize→deserialize round-trip
4. Mention processing (`@player`, `@everyone`, `@here`) + notifications — runs **before** placeholder expansion so an `@` inside an expanded value (PAPI, anvil-renamed item in `^item`) can never trigger a mention
5. Placeholder processing (`^loc`, `^health`, etc.) via `PlaceholderService.processEscaped` (input already escaped; only trusted config formats carry tags)
6. Chat log to DB (async)
7. LuckPerms prefix/suffix via `PrefixFormatter.toMiniMessage()`, **inlined** into the format string (not inserted as closed components) so an unclosed colour/gradient in a suffix flows into `{player}`/`{message}`
8. Build final Component: `{player}`/`{message}` stay components (player input was already escaped in step 3, so it never renders as MiniMessage) but inherit the open colour from the inlined prefix/suffix
9. Route: LOCAL = players in radius (squared distance), GLOBAL = `Bukkit.broadcast()`

### Database

Single `Database` class with async writes via a single-thread executor (`zoChat-DB` daemon thread). Two repositories:
- **ChatLogRepository**: `chat_logs(id, player_uuid TEXT, message TEXT, timestamp)`
- **OfflineMessageRepository**: `offline_messages(id, sender_uuid, receiver_uuid, message, timestamp)`

SQLite default (`chat.db`); MySQL supported via bundled+relocated connector. All DB I/O is async — reads deliver results back to main thread via `Bukkit.getScheduler().runTask()`. `conn()` revalidates MySQL connections with `isValid()` and reopens dead ones (a connection killed by `wait_timeout` still reports `isClosed() == false`); the JDBC URL deliberately has no `autoReconnect=true`.

## Configuration Files

- `config.yml`: Main configuration (chat formats, cooldowns, database, banned words, mention settings, join/quit, PlaceholderAPI toggles)
- `messages/messages_ru.yml` and `messages/messages_en.yml`: Player-facing localized messages. Language set via `config.yml: message: ru/en`
- `welcome_messages/welcome_messages.yml`: Welcome messages shown on join
- `placeholders.yml`: `^` placeholders — built-in + custom (PAPI-backed) + world-name translation
- `announcer.yml`: Timed chat broadcasts (announcers + announcements-by-ID)
- `bubble.yml`: Floating bubble chat (TextDisplay) settings
- `plugin.yml`: Plugin metadata, command definitions, permissions

## Important Implementation Details

### MiniMessage Format Strings

All chat formats use MiniMessage syntax:
- HEX colors: `<#ff5555>text</#ff5555>`
- Gradients: `<gradient:#55ff55:#aaffaa>text</gradient>`
- Placeholders in formats: `{prefix}`, `{suffix}`, `{player}`, `{message}`

Components are built using `Component.replaceText()` to substitute placeholders.

### LuckPerms Integration

LuckPerms prefix/suffix meta may contain **legacy color codes** (`&c`, `§c`, `&#ff5555`, `§x§f§f...`) AND/OR **MiniMessage tags** (`<#ff5555>`, `<gradient:...>`). MiniMessage does NOT parse legacy codes, and the legacy serializer does NOT parse MiniMessage — so use `PrefixFormatter`:
- `PrefixFormatter.toMiniMessage(meta)` — translates legacy codes to MiniMessage tags while **preserving** existing MiniMessage tags (leaves them unclosed, like legacy). `ChatService` inlines the result into the format so an unclosed `<#hex>` in a suffix recolours the following `{message}`. This is how a player's message colour is driven by their suffix.
- `PrefixFormatter.format(meta)` — legacy → standalone `Component` (closed), when you just need the rendered prefix/suffix on its own.

Never pass raw LuckPerms strings to `miniMessage.deserialize()` directly, or legacy codes render as raw characters (issue #3).

### Anti-Spam System

`CooldownService` (Kotlin) holds a `ConcurrentHashMap<UUID, ConcurrentHashMap<Channel, Long>>` — cooldowns are **per channel** (`LOCAL`/`GLOBAL`/`PRIVATE`), so a global message no longer blocks a following local/private one. `ConcurrentHashMap` because it's read from PlaceholderAPI requests (which can run off-thread). `isOnCooldown()` checks and records "now" in one call (only when allowed). Cleared per-player on quit, fully reset on reload/disable. Cooldown times are **seconds** in config, compared in **milliseconds** in code. The cooldown is checked **after** the banned-words filter, so a rejected message doesn't burn it. Bypass permission: `zochat.spam.bypass`.

### Mention System (MentionHandler.java)

- `@PlayerName` — partial name matching, max 5 per message
- `@everyone` (aliases: `@все`, `@all`) — requires `zochat.mention.everyone`
- `@here` (aliases: `@здесь`) — players within `here-radius`, requires `zochat.mention.here`

### Placeholder System (PlaceholderService.java + PlaceholderConfig.kt)

Placeholders start with `^` and are configured in their own **`placeholders.yml`** (not config.yml). `PlaceholderConfig` loads it: a master `enabled`, a `world-names` translation map (real world → display name, applied to `^world` and the `{world}` token in `^loc`), a `builtin` section (loc/world/time/health/ping/biome/item — resolved from player state) and a `custom` section. **Custom** placeholders are PlaceholderAPI-backed: `value` (a `%...%` string resolved per player) is spliced into `format` as `{value}`.

Matching uses a single regex built from all enabled aliases, **longest-first with a trailing `(?![\p{L}\p{N}])` lookahead** (`buildPattern`, package-private/static, unit-tested) — so `^world` wins over the `^w` alias and `^w` never matches inside `^wave`. Unknown/no-permission tokens are left literal. The player's text is escaped (`escapeTags`) before matching, so only trusted config formats carry MiniMessage. `placeholders.yml` does NOT use `syncWithDefaults` (custom IDs / world-names are user-authored keys).

### Banned Words (BannedWordsFilter.kt)

Modes: `exact`, `contains`, `smart` (default). Actions: `block` (default), `replace`. Normalization (removes spaces/repeats, L33t speak, Cyrillic look-alikes) is toggled by `banned-words.normalize` (default true). Detection keeps a position map back to the original text, so detect and censor share one match set — `replace` censors the matched original span (including bypass spans like `b a d`) and does NOT block. `smart` adds a word-boundary check on the original text (so `ass` does not match inside `class`). Supports `regex:` prefix. `normalize()` and `apply()` are public static (companion `@JvmStatic`) for testability.

## Testing

JUnit 5 tests in `src/test/`. Adventure API on test classpath via Gradle `extendsFrom` trick (paper-api is `compileOnly`, extended into `testImplementation`).

```bash
./gradlew test
```

Current tests: PrefixFormatterTest (legacy code parsing), BannedWordsFilterTest (normalization + matching).

## Common Pitfalls

- Always use `miniMessage.deserialize()` for format strings before sending to players
- Chat formats must include `{prefix}`, `{suffix}`, `{player}`, `{message}` placeholders
- Database type in config.yml must be `"sqlite"` or `"mysql"` (case-insensitive)
- Cooldown times are **seconds** in config, **milliseconds** in code
- Local chat radius is **squared** in distance check (avoid sqrt)
- AsyncChatEvent must be cancelled, then `send` re-dispatched to the main thread (it reads player state)
- Never `miniMessage.deserialize()` raw player text — escape it first (`escapeTags`), or tags injected in chat/PMs render (incl. `<click:run_command>`)
- LuckPerms legacy codes → `PrefixFormatter` (never `miniMessage.deserialize()` directly)
- MySQL driver is relocated by Shadow — explicit `Class.forName("zorahm.zochat.libs.mysql.cj.jdbc.Driver")` in Database.java

## Code Style

### Comments: explain WHY, not WHAT

The code already says *what* it does — don't restate it in a comment. Comments must capture the reason the reader can't see: the constraint, the gotcha, the non-obvious consequence.

- Bad (restates the code): `// fetch the prefix from LuckPerms`
- Good (explains the reason): `// LuckPerms returns legacy color codes that MiniMessage can't parse — convert first (issue #3)`

Write the *why*: why this approach over the obvious one, why an edge case is handled, which bug/issue a workaround addresses. If a line of code is self-explanatory, leave it uncommented. Write comments in **English**.

### Language: Kotlin for new code

Write **new** files in **Kotlin** (`src/main/kotlin/...`). Existing Java files stay Java — edit them in place rather than converting wholesale. Java and Kotlin coexist in the same Gradle build (joint compilation): Kotlin can call Java and vice-versa. The kotlin-stdlib is bundled and relocated by Shadow (`zorahm.zochat.libs.kotlin`).

For Kotlin classes consumed from Java, keep the Java-facing API stable: a `val isBlocked` Boolean property exposes `isBlocked()`, other `val`s expose `getX()`; use `@JvmStatic`/`@JvmOverloads` on companion functions that Java calls.

## Dependencies

- **Paper API 1.21.10** (compileOnly, provided by server)
- **LuckPerms API 5.4** (compileOnly, required dependency)
- **Adventure API** (transitive from Paper, compileOnly)
- **MySQL Connector/J 9.1.0** (implementation, bundled+relocated by Shadow)
- **SQLite JDBC** (provided by Paper runtime)
- **JUnit Jupiter 5.10.2** (testImplementation)
