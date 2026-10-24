# CLAUDE.md

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

### Core Plugin Lifecycle (ZoChatPlugin.kt)

Main entry point at `src/main/kotlin/zorahm/zochat/ZoChatPlugin.kt`. On enable:
1. Displays ASCII banner using Adventure Components
2. Checks LuckPerms is enabled (`depend` guarantees it's installed, but it can fail to enable) — disables the plugin otherwise
3. Preloads classes (`ClassPreloader`), then initializes: ChatConfig, Messages (ru/en), Database (SQLite/MySQL), repositories
4. Creates services: BannedWordsFilter, MentionHandler, PapiHook, PlaceholderService, BubbleService, ChatService, PrivateMessageService, WelcomeMessages, AnnouncerService, CommandGuardConfig
5. Registers commands: `/chat`, `/global` (`/g`), `/local` (`/l`), `/msg`, `/reply` (`/r`), `/chatlog`, `/bubble` (`/b`)
6. Registers event listeners: ChatListener, PresenceListener, CommandGuardListener, SayListener
7. Registers the `%zochat_...%` expansion — only when PlaceholderAPI is actually installed

### Package Layout

All sources are Kotlin, in `src/main/kotlin/...` and `src/test/kotlin/...`. The plugin was ported from a Java/Kotlin hybrid; the whole codebase is now a single language.

```
zorahm.zochat
├── ZoChatPlugin.kt                   // lifecycle + manual DI wiring only
├── config/
│   ├── ChatConfig.kt                // typed getters over config.yml
│   ├── Messages.kt                  // ru/en player-facing strings
│   └── ConfigFiles.kt               // saveDefault + non-destructive merge of missing keys
├── chat/
│   ├── ChatService.kt               // THE single send pipeline (local + global + /g + /l)
│   ├── ChatChannel.kt               // enum LOCAL/GLOBAL
│   ├── ChatListener.kt              // AsyncChatEvent -> route() -> ChatService; chatAs() for /g /l /bubble
│   ├── GlobalCommand.kt             // /g -> ChatListener.chatAs(GLOBAL)
│   ├── LocalCommand.kt              // /l -> ChatListener.chatAs(LOCAL)
│   ├── PrefixFormatter.kt           // LuckPerms legacy -> MiniMessage / Component
│   ├── MentionHandler.kt            // @player/@everyone/@here
│   ├── MentionTabCompleter.kt       // tab completion for @mentions
│   ├── PlaceholderService.kt        // ^loc/^world/custom; regex match; escapes player input
│   ├── BannedWordsFilter.kt          // exact/contains/smart filter + position-mapped normalization
│   ├── CooldownService.kt            // per-channel anti-spam cooldowns (ConcurrentHashMap)
│   ├── PlaceholderConfig.kt          // loads placeholders.yml (builtin/custom ^ defs + world-names)
│   ├── PapiHook.kt                   // the ONLY place that touches PlaceholderAPI classes
│   └── ZoChatExpansion.kt            // our own %zochat_...% expansion (sync, no-DB values only)
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
├── guard/
│   ├── CommandGuardConfig.kt         // loads commands.yml (blocked list + toggles)
│   ├── CommandGuard.kt               // pure blocked/allowed decision incl. namespace forms (unit-tested)
│   └── CommandGuardListener.kt       // cancels blocked commands + hides them from tab completion
├── say/
│   └── SayListener.kt                // reformats vanilla /say (player + console) like chat
├── privatemsg/
│   ├── PrivateMessageService.kt      // /msg + /reply core, offline hand-off
│   ├── MsgCommand.kt
│   └── ReplyCommand.kt
├── presence/
│   ├── PresenceListener.kt           // join + quit + welcome + offline delivery
│   └── WelcomeMessages.kt            // welcome_messages.yml loader
├── storage/
│   ├── Database.kt                   // single connection, async writes via single-thread executor
│   ├── ChatLogRepository.kt          // chat_logs table
│   └── OfflineMessageRepository.kt   // offline_messages table
├── command/
│   ├── ChatCommand.kt                // /chat reload|help
│   └── ChatLogCommand.kt             // /chatlog <player|clear>
└── util/
    ├── Sounds.kt                     // safe Sound.valueOf -> Optional<Sound>
    └── ClassPreloader.kt             // eager class load at onEnable — lazy loads read the shaded jar on the main thread mid-tick (watchdog stall on slow-I/O hosts)
```

### Chat Flow (ChatService.kt)

All chat messages (natural chat, /g, /l) go through a single `ChatService.send(player, rawMessage, channel)`. Natural chat arrives on `AsyncChatEvent` (off-thread); `ChatListener` cancels the event and re-dispatches `send` to the main thread, because the pipeline reads player state (inventory/health/location) and plays sounds. `/g`, `/l` and `/bubble` don't call the service directly: `ChatListener.chatAs()` re-sends their text through `Player.chat()`, which fires `AsyncChatEvent` **synchronously** on the main thread, so mute/anti-spam/logging plugins see it like typed chat. A `forced` map entry (set only for that synchronous call; async events ignore it) carries the command's channel; a leading `/` gets a space so `Player.chat()` doesn't run it as a command. The order:
0. Channel permission (`zochat.local` / `zochat.global`, from `ChatChannel.permission`)
1. Banned words filter (block or replace mode) — before the cooldown so a rejected message doesn't burn it
2. Anti-spam cooldown check (per-channel via `CooldownService`; bypass permission: `zochat.spam.bypass`)
3. The player's text is **escaped once** (`miniMessage.escapeTags`) — player-typed `<...>` is never parsed as MiniMessage (no colour/`<click:run_command>` injection). Everything below works on that escaped string; there is no Component→serialize→deserialize round-trip
4. Mention processing (`@player`, `@everyone`, `@here`) — runs **before** any placeholder expansion so an `@` inside an expanded value (PAPI, anvil-renamed item in `^item`) can never trigger a mention. Notifications go only to players who will receive the message (local radius for LOCAL)
5. Player-typed `%...%` (opt-in, permission-gated) via `PapiHook.applyToPlayerText` — each value is flattened to plain text and escaped on its own; the player's own `&c` stays literal
6. Placeholder processing (`^loc`, `^health`, etc.) via `PlaceholderService.processEscaped` (input already escaped; only trusted config formats carry tags; `^item` names are escaped and banned-word-filtered)
7. Chat log to DB (async)
8. LuckPerms prefix/suffix via `PrefixFormatter.toMiniMessage()`, **inlined** into the format string (`formats.local-chat`/`formats.global-chat` from the messages file) (not inserted as closed components) so an unclosed colour/gradient in a suffix flows into `{player}`/`{message}`
9. Build final Component: `{player}`/`{message}` stay components (player input was already escaped in step 3, so it never renders as MiniMessage) but inherit the open colour from the inlined prefix/suffix
10. Route: LOCAL = players in radius (squared distance) + console, GLOBAL = `Bukkit.broadcast()`

`ChatListener.route()` (pure, unit-tested) decides the channel. With a forced intent (from `chatAs`) the command's channel wins and the text is taken literally (`/l !hi` stays local), `BUBBLE` only shows a bubble (`ChatService.sendBubble`: filter + `BUBBLE` cooldown). Otherwise: `!text` → GLOBAL, plain → LOCAL if local chat is on, else GLOBAL; a bare `!` is swallowed; `!` while `global-chat.enabled: false` is refused; with BOTH channels off zoChat leaves chat to vanilla.

### Database

Single `Database` class with async writes via a single-thread executor (`zoChat-DB` daemon thread). Two repositories:
- **ChatLogRepository**: `chat_logs(id, player_uuid, message, timestamp)` — `recentFor` orders by `id` (timestamps are 1-second); callbacks get `null` on a DB error
- **OfflineMessageRepository**: `offline_messages(id, sender_uuid, receiver_uuid, message, timestamp)` — `drainFor` selects+deletes in one transaction; `restore` puts drained rows back (player quit mid-drain)

DDL is **per dialect** (`Database.isMysql`): SQLite `INTEGER PRIMARY KEY AUTOINCREMENT` + `CREATE INDEX IF NOT EXISTS`; MySQL `BIGINT AUTO_INCREMENT`, `VARCHAR(36)` uuids (MySQL can't index TEXT), inline `INDEX`, `utf8mb4`. Times are written explicitly via `Database.bindTime`/`readTime`: SQLite stores UTC text `yyyy-MM-dd HH:mm:ss` (sqlite-jdbc's `getTimestamp()` reads CURRENT_TIMESTAMP's UTC text as local time), MySQL a native DATETIME.

SQLite default (`chat.db`); MySQL supported via bundled+relocated connector. All DB I/O is async — reads deliver results back to main thread via `Bukkit.getScheduler().runTask()`. `conn()` revalidates MySQL connections with `isValid()` and reopens dead ones (a connection killed by `wait_timeout` still reports `isClosed() == false`); the JDBC URL deliberately has no `autoReconnect=true`, and carries `allowPublicKeyRetrieval=true` (MySQL 8 auth without SSL).

## Configuration Files

- `config.yml`: Behaviour only (channels, cooldowns, database, banned words, mention formats/permissions, join/quit toggles, PlaceholderAPI toggles) — no language-dependent text
- `messages/messages_ru.yml` and `messages/messages_en.yml`: Every player-visible text, incl. the chat/PM/join/quit formats (`formats.*`) and mention action-bar notifications (`mention.notify*`). Both moved out of config.yml; `ChatConfig.customisedMovedTexts()` (unit-tested) carries values the admin changed from the old Russian defaults into the active language's file before `syncWithDefaults` drops the old keys. Language set via `config.yml: message: ru/en`
- `welcome_messages/welcome_messages.yml`: Welcome messages shown on join
- `placeholders.yml`: `^` placeholders — built-in + custom (PAPI-backed) + world-name translation
- `announcer.yml`: Timed chat broadcasts (announcers + announcements-by-ID)
- `bubble.yml`: Floating bubble chat (TextDisplay) settings
- `commands.yml`: Console-only command list for the command guard
- `plugin.yml`: Plugin metadata, command definitions, permissions. Descriptions here surface in the vanilla `/help` for every player — keep them **English**, unlike the localized `messages_*.yml`

## Important Implementation Details

### MiniMessage Format Strings

All chat formats use MiniMessage syntax:
- HEX colors: `<#ff5555>text</#ff5555>`
- Gradients: `<gradient:#55ff55:#aaffaa>text</gradient>`
- Placeholders in formats: `{prefix}`, `{suffix}`, `{player}`, `{message}`

Components are built using `Component.replaceText()` to substitute placeholders.

### LuckPerms Integration

LuckPerms prefix/suffix meta may contain **legacy color codes** (`&c`, `§c`, `&#ff5555`, `§x§f§f...`) AND/OR **MiniMessage tags** (`<#ff5555>`, `<gradient:...>`). MiniMessage does NOT parse legacy codes, and the legacy serializer does NOT parse MiniMessage — so use `PrefixFormatter`:
- `PrefixFormatter.toMiniMessage(meta)` — translates legacy codes to MiniMessage tags while **preserving** existing MiniMessage tags (leaves them unclosed, like legacy). `ChatService` inlines the result into the format so an unclosed `<#hex>` in a suffix recolours the following `{message}`. This is how a player's message colour is driven by their suffix. Legacy semantics are kept: a colour code ends the decorations that earlier legacy codes in the same string opened (`&c&l[Admin]&7` emits `<!bold>` before `<gray>`), but never touches a `<bold>` from the surrounding format.
- `PrefixFormatter.format(meta)` — legacy → standalone `Component` (closed), when you just need the rendered prefix/suffix on its own.

Never pass raw LuckPerms strings to `miniMessage.deserialize()` directly, or legacy codes render as raw characters (issue #3).

### Anti-Spam System

`CooldownService` (Kotlin) holds a `ConcurrentHashMap<UUID, ConcurrentHashMap<Channel, Long>>` — cooldowns are **per channel** (`LOCAL`/`GLOBAL`/`PRIVATE`/`BUBBLE`), so a global message no longer blocks a following local/private one. `ConcurrentHashMap` because it's read from PlaceholderAPI requests (which can run off-thread). `isOnCooldown()` checks and records "now" in one call (only when allowed). Cleared per-player on quit, fully reset on reload/disable. Cooldown times are **seconds** in config, compared in **milliseconds** in code. The cooldown is checked **after** the banned-words filter, so a rejected message doesn't burn it. Bypass permission: `zochat.spam.bypass`.

### Mention System (MentionHandler.kt)

- `@PlayerName` — partial name matching, max 5 per message
- `@everyone` (aliases: `@все`, `@all`) — requires `zochat.mention.everyone`
- `@here` (aliases: `@здесь`) — players within `here-radius`, requires `zochat.mention.here`
- `EVERYONE_PATTERN`/`HERE_PATTERN` are whole-word (`@Allan` is not `@all`) and `UNICODE_CASE` (`@ВСЕ`); vanished players (`canSee`) can't be mentioned or tab-completed

### Placeholder System (PlaceholderService.kt + PlaceholderConfig.kt)

Placeholders start with `^` and are configured in their own **`placeholders.yml`** (not config.yml). `PlaceholderConfig` loads it: a master `enabled`, a `world-names` translation map (real world → display name, applied to `^world` and the `{world}` token in `^loc`), a `builtin` section (loc/world/time/health/ping/biome/item — resolved from player state) and a `custom` section. **Custom** placeholders are PlaceholderAPI-backed: `value` (a `%...%` string resolved per player via `PapiHook.applyColored` — legacy colours kept, MiniMessage tags in the value escaped, since values can be player-made) is spliced into `format` as `{value}`.

Matching uses a single regex built from all enabled aliases, **longest-first with a trailing `(?![\p{L}\p{N}])` lookahead** (`buildPattern`, package-private/static, unit-tested) — so `^world` wins over the `^w` alias and `^w` never matches inside `^wave`. Unknown/no-permission tokens are left literal. The player's text is escaped (`escapeTags`) before matching, so only trusted config formats carry MiniMessage. `placeholders.yml` does NOT use `syncWithDefaults` (custom IDs / world-names are user-authored keys).

### Banned Words (BannedWordsFilter.kt)

Modes: `exact`, `contains`, `smart` (default). Actions: `block` (default), `replace`. Normalization (removes separators, L33t speak, Cyrillic look-alikes) builds **runs** of a repeated letter; a run of 3+ in the message matches any count in the word, 1 vs 2 must match exactly (`asss` hits `ass`, `good` doesn't hit `god`). It is toggled by `banned-words.normalize` (default true). Detection keeps a position map back to the original text, so detect and censor share one match set — `replace` censors the matched original span (including bypass spans like `b a d`) and does NOT block. `smart` adds a word-boundary check on the original text (so `ass` does not match inside `class`). Supports `regex:` prefix. `normalize()` and `apply()` are public static (companion `@JvmStatic`) for testability.

## Testing

JUnit 5 tests in `src/test/`. Adventure API on test classpath via Gradle `extendsFrom` trick (paper-api is `compileOnly`, extended into `testImplementation`).

```bash
./gradlew test
```

Current tests: PrefixFormatterTest (legacy code parsing, decoration reset), BannedWordsFilterTest (normalization + run matching), PlaceholderMatchTest (`buildPattern` longest-first matching), MentionPatternTest (whole-word @everyone/@here), ChatRouteTest (`!`/channel routing, forced intents), PapiPlayerTextTest (`PapiHook.expandEscaped`/`expandColored`), MovedTextsTest (config.yml -> messages migration), AnnouncementSelectorTest (SEQUENTIAL/RANDOM rotation), CommandGuardTest (blocked/namespaced/`execute run` decisions), StorageTest (repositories on a real SQLite file — `sqlite-jdbc` is a testImplementation dep since Paper only provides it at runtime).

## CI

`.github/workflows/build.yml` builds every push/PR with `./gradlew clean build` and uploads the JAR as a run artifact. `.github/workflows/release.yml` rebuilds on a `v*` tag (or a published release) and attaches the JAR to that GitHub Release.

The wrapper needs its executable bit **in git**, or the runner fails with `./gradlew: Permission denied` — Windows checkouts drop it, so re-add it with `git update-index --chmod=+x gradlew` if it is ever lost.

## Common Pitfalls

- Always use `miniMessage.deserialize()` for format strings before sending to players
- Chat formats must include `{prefix}`, `{suffix}`, `{player}`, `{message}` placeholders
- Database type in config.yml must be `"sqlite"` or `"mysql"` (case-insensitive)
- Cooldown times are **seconds** in config, **milliseconds** in code
- Local chat radius is **squared** in distance check (avoid sqrt)
- AsyncChatEvent must be cancelled, then `send` re-dispatched to the main thread (it reads player state)
- Never `miniMessage.deserialize()` raw player text — escape it first (`escapeTags`), or tags injected in chat/PMs render (incl. `<click:run_command>`). That includes player text read back from the DB (`/chatlog`, offline PMs), anvil item names (`^item`) and player-typed PAPI values
- `PlayerCommandPreprocessEvent` fires **before** Bukkit's permission check — a listener that takes over a command (`SayListener` for /say) must check the command's permission itself
- DDL is per dialect — `AUTOINCREMENT` is SQLite-only and MySQL can't index TEXT; branch on `Database.isMysql`
- LuckPerms legacy codes → `PrefixFormatter` (never `miniMessage.deserialize()` directly)
- MySQL driver is relocated by Shadow — explicit `Class.forName("zorahm.zochat.libs.mysql.cj.jdbc.Driver")` in Database.kt

## Code Style

### Comments: explain WHY, not WHAT

The code already says *what* it does — don't restate it in a comment. Comments must capture the reason the reader can't see: the constraint, the gotcha, the non-obvious consequence.

- Bad (restates the code): `// fetch the prefix from LuckPerms`
- Good (explains the reason): `// LuckPerms returns legacy color codes that MiniMessage can't parse — convert first (issue #3)`

Write the *why*: why this approach over the obvious one, why an edge case is handled, which bug/issue a workaround addresses. If a line of code is self-explanatory, leave it uncommented. Write comments in **English**.

### Language: Kotlin throughout

Everything is **Kotlin** (`src/main/kotlin/...`, tests in `src/test/kotlin/...`) — write new code in Kotlin too. The kotlin-stdlib is bundled and relocated by Shadow (`zorahm.zochat.libs.kotlin`).

Kotlin was adopted by porting a Java/Kotlin hybrid, so some API shapes still mirror their Java origins. Keep the conventions already in use:

- Boolean properties keep the `is` prefix (`val isBlocked`, `val isAntiSpamEnabled`) so `if (x.isBlocked)` reads naturally.
- Pure helpers that exist to be unit-tested live in a `companion object` (or an `object`) as `@JvmStatic` functions — e.g. `BannedWordsFilter.normalize`, `PlaceholderService.buildPattern`. Don't make them `internal`: Kotlin mangles `internal` names (`buildPattern$module`), which is pointless friction for the tests.
- `@JvmOverloads` where a helper has defaulted parameters (`BannedWordsFilter.normalize(text, leet = true)`).
- Bukkit `getX()` accessors map to properties (`player.location`, `config.sayFormat`); `setX()` to assignments (`event.isCancelled = true`).
- `Database.conn()`/`Database.logger` are `internal` — there is no package-private in Kotlin, and only the two repositories need them.

## Dependencies

- **Paper API 1.21.10** (compileOnly, provided by server)
- **LuckPerms API 5.4** (compileOnly, required dependency)
- **Adventure API** (transitive from Paper, compileOnly)
- **MySQL Connector/J 9.1.0** (implementation, bundled+relocated by Shadow)
- **SQLite JDBC** (provided by Paper runtime)
- **JUnit Jupiter 5.10.2** (testImplementation)
