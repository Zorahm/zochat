package zorahm.zochat.chat

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Isolates every PlaceholderAPI reference behind one class so the rest of the plugin runs
 * fine when PAPI isn't installed. The me.clip bytecode lives only in the available branches
 * of [apply]/[applyToPlayerText], guarded by [isAvailable] — so the JVM never resolves the
 * PlaceholderAPI class (no NoClassDefFoundError) on servers without the plugin.
 */
class PapiHook {

    // PAPI is a softdepend, so it's enabled before zoChat loads — a one-time check is reliable.
    val isAvailable: Boolean = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")

    /** For admin-authored text (formats, announcements): values may carry colours. */
    fun apply(player: Player, text: String): String {
        if (!isAvailable || text.isEmpty()) return text
        // Placeholders like %vault_prefix% commonly return legacy colour codes that MiniMessage
        // can't parse — convert to MiniMessage tags first (issue #3), leaving any MiniMessage
        // already in the text untouched.
        return PrefixFormatter.toMiniMessage(PlaceholderAPI.setPlaceholders(player, text))
    }

    /** For a player's own, already tag-escaped chat text — see [expandEscaped]. */
    fun applyToPlayerText(player: Player, escaped: String): String {
        if (!isAvailable || escaped.isEmpty()) return escaped
        return expandEscaped(escaped) { PlaceholderAPI.setPlaceholders(player, it) }
    }

    /** For admin text whose placeholder VALUES may be player-made — see [expandColored]. */
    fun applyColored(player: Player, text: String): String {
        if (!isAvailable || text.isEmpty()) return text
        return expandColored(text) { PlaceholderAPI.setPlaceholders(player, it) }
    }

    companion object {
        // PAPI identifiers never contain whitespace, so "100% sure 50%" isn't taken for a token.
        private val TOKEN: Pattern = Pattern.compile("%[^%\\s]+%")
        private val PLAIN: PlainTextComponentSerializer = PlainTextComponentSerializer.plainText()
        private val MINI: MiniMessage = MiniMessage.miniMessage()

        /**
         * Expands %...% tokens inside a player's tag-escaped text one by one. Converting the whole
         * string (like [apply] does) turned the player's own "&c" into a "<red>" that the chat
         * pipeline then showed literally. Each value is flattened to plain text and escaped, so
         * neither the player nor a placeholder value can smuggle in colours or <click>.
         */
        @JvmStatic
        fun expandEscaped(escaped: String, resolve: (String) -> String): String =
            expandTokens(escaped, resolve) { MINI.escapeTags(PLAIN.serialize(PrefixFormatter.format(it))) }

        /**
         * Expands %...% inside trusted admin text (a custom ^ placeholder's `value`). The admin
         * picks the placeholder, but its value can be player-made — a nickname, a town name — so
         * legacy colours in it are kept while its MiniMessage tags are escaped (no <click>).
         */
        @JvmStatic
        fun expandColored(text: String, resolve: (String) -> String): String =
            expandTokens(PrefixFormatter.toMiniMessage(text), resolve) { PrefixFormatter.toMiniMessage(MINI.escapeTags(it)) }

        private fun expandTokens(text: String, resolve: (String) -> String, render: (String) -> String): String {
            val matcher = TOKEN.matcher(text)
            val out = StringBuilder()
            while (matcher.find()) {
                val token = matcher.group()
                val value = resolve(token)
                val replacement = if (value == token) token else render(value)
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement))
            }
            matcher.appendTail(out)
            return out.toString()
        }
    }
}
