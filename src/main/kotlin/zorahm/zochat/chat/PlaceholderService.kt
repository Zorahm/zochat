package zorahm.zochat.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.Messages
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

class PlaceholderService(
    private val plugin: Plugin,
    private val config: PlaceholderConfig,
    private val messages: Messages,
    private val papi: PapiHook,
    private val bannedWords: BannedWordsFilter
) {

    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
    private val plainText: PlainTextComponentSerializer = PlainTextComponentSerializer.plainText()
    private val realtimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    private val byAlias = HashMap<String, PlaceholderDef>()
    private var pattern: Pattern? = null

    init {
        rebuild()
    }

    // Reload placeholders.yml and rebuild the match pattern so /chat reload picks up new aliases/customs.
    fun reload() {
        config.reload()
        rebuild()
    }

    private fun rebuild() {
        byAlias.clear()
        val tokens = ArrayList<String>()
        for (def in config.definitions()) {
            if (!def.enabled) {
                continue
            }
            register(def.name, def, tokens)
            for (alias in def.aliases) {
                register(alias.lowercase(), def, tokens)
            }
        }
        pattern = buildPattern(tokens)
    }

    private fun register(alias: String, def: PlaceholderDef, tokens: MutableList<String>) {
        if (alias.isEmpty() || byAlias.containsKey(alias)) {
            return
        }
        byAlias[alias] = def
        tokens.add(alias)
    }

    fun processPlaceholders(player: Player, message: String): Component {
        // Escape the player's text so their literal '<...>' is never parsed as MiniMessage — otherwise
        // anyone could inject colours, gradients or <click:run_command:...> that runs as the viewer.
        // Only trusted, config-defined placeholder values (substituted below) are meant to carry tags.
        return processEscaped(player, miniMessage.escapeTags(message))
    }

    // The caller has ALREADY tag-escaped the player text. ChatService escapes once and then splices
    // trusted mention tags into the string — escaping again here would neuter those tags.
    fun processEscaped(player: Player, escaped: String): Component {
        val current = pattern
        if (!config.isEnabled || current == null) {
            return miniMessage.deserialize(escaped)
        }

        val matcher = current.matcher(escaped)
        val out = StringBuilder()
        while (matcher.find()) {
            val def = byAlias[matcher.group(1).lowercase()]
            var replacement: String? = null
            if (def != null && (def.permission.isEmpty() || player.hasPermission(def.permission))) {
                replacement = if (def.builtin) builtin(player, def) else custom(player, def)
            }
            // Unknown alias / no permission / unresolved → keep the literal token as the player typed it.
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement ?: matcher.group()))
        }
        matcher.appendTail(out)
        return miniMessage.deserialize(out.toString())
    }

    // Custom placeholders resolve their PlaceholderAPI 'value' per player and splice it into 'format'.
    // applyColored, not apply: the value can be player-made text and must not carry tags.
    private fun custom(player: Player, def: PlaceholderDef): String {
        val value = papi.applyColored(player, def.value)
        return def.format.replace("{value}", value)
    }

    private fun builtin(player: Player, def: PlaceholderDef): String? {
        val format = def.format
        return when (def.name) {
            // Block coordinates (floor), matching F3 — "%.0f" rounded 10.6 up to 11 and showed -0.
            "loc" -> format
                .replace("{x}", player.location.blockX.toString())
                .replace("{y}", player.location.blockY.toString())
                .replace("{z}", player.location.blockZ.toString())
                .replace("{world}", config.worldName(player.world.name))

            "world" -> format.replace("{world}", config.worldName(player.world.name))

            "time" -> {
                val worldTime = player.world.time
                val hours = (worldTime / 1000 + 6) % 24
                val minutes = worldTime % 1000 * 60 / 1000
                val gameTime = String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
                val realTime = realtimeFormatter.format(Instant.now())
                format
                    .replace("{time}", gameTime)
                    .replace("{realtime}", realTime)
            }

            "health" -> {
                val health = player.health
                val maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH)
                val maxHealth = maxHealthAttr?.value ?: 20.0
                format
                    .replace("{health}", String.format(Locale.ROOT, "%.1f", health))
                    .replace("{maxhealth}", String.format(Locale.ROOT, "%.1f", maxHealth))
            }

            "ping" -> format.replace("{ping}", player.ping.toString())

            // Biome.name() (legacy enum) is marked for removal — use the key value from the registry instead.
            "biome" -> {
                val biome = player.location.block.biome.key.key.replace("_", " ")
                format.replace("{biome}", biome)
            }

            "item" -> {
                val item = player.inventory.itemInMainHand
                if (item.type.isAir) {
                    format
                        .replace("{item}", messages.get("placeholders.empty-hand"))
                        .replace("{amount}", "0")
                } else {
                    val materialName = item.type.name.lowercase().replace("_", " ")
                    // displayName() returns a Component; extract plain text from it, otherwise legacy color
                    // codes of a custom name would end up as raw characters in MiniMessage format (like issue #3).
                    val shownName = item.itemMeta?.takeIf { it.hasDisplayName() }?.displayName()
                    val itemName = if (shownName != null) {
                        // An anvil name is player-typed text: escape it, or "<click:run_command:...>"
                        // in a name would render for everyone. It also never passed the banned-words
                        // filter (that only sees the raw message) — censor it, or fall back to the
                        // material name when the filter would block it.
                        val filtered = bannedWords.checkMessage(plainText.serialize(shownName))
                        if (filtered.isBlocked) materialName else miniMessage.escapeTags(filtered.processedMessage)
                    } else {
                        materialName
                    }
                    format
                        .replace("{item}", itemName)
                        .replace("{amount}", item.amount.toString())
                }
            }

            // A built-in entry whose name isn't one we know how to resolve — leave it literal.
            else -> null
        }
    }

    companion object {
        // Public + @JvmStatic (like BannedWordsFilter's pure helpers) so the boundary behaviour is
        // unit-testable without a live server.
        @JvmStatic
        fun buildPattern(tokens: List<String>): Pattern? {
            if (tokens.isEmpty()) {
                return null
            }
            // Longest first so "^world" wins over the alias "^w"; the trailing lookahead stops "^w" from
            // matching inside "^world"/"^wave" — fixes the old substring-replace that ate longer tokens.
            // compareByDescending is stable, so equal-length tokens keep their config order.
            val sorted = tokens.sortedWith(compareByDescending { it.length })
            val sb = StringBuilder("\\^(")
            for (i in sorted.indices) {
                if (i > 0) {
                    sb.append('|')
                }
                sb.append(Pattern.quote(sorted[i]))
            }
            sb.append(")(?![\\p{L}\\p{N}])")
            return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
        }
    }
}
