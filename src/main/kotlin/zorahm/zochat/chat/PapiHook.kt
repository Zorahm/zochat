package zorahm.zochat.chat

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import zorahm.zochat.util.PlayerText

/**
 * Isolates every PlaceholderAPI reference behind one class so the rest of the plugin runs
 * fine when PAPI isn't installed. The me.clip bytecode is only reached behind an [isAvailable]
 * check — so the JVM never resolves the PlaceholderAPI class (no NoClassDefFoundError) on
 * servers without the plugin.
 */
class PapiHook {

    // PAPI is a softdepend, so it's enabled before zoChat loads — a one-time check is reliable.
    val isAvailable: Boolean = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")

    /** For admin-authored (trusted) MiniMessage strings such as chat formats. */
    fun apply(player: Player, text: String): String {
        if (!isAvailable || text.isEmpty()) return text
        // Placeholders like %vault_prefix% commonly return legacy colour codes that MiniMessage
        // can't parse — convert to MiniMessage tags first (issue #3), leaving any MiniMessage
        // already in the text untouched.
        return PrefixFormatter.toMiniMessage(PlaceholderAPI.setPlaceholders(player, text))
    }

    /**
     * For player-typed text: expands its %...% tokens and returns the result ALREADY ESCAPED for
     * MiniMessage. Running the whole message through [apply] turned a typed "&r" into <reset>, and the
     * escaping that followed showed every PAPI colour as a raw "<gold>" tag.
     */
    fun escapeWithPlaceholders(player: Player, text: String): String {
        if (!isAvailable) return PlayerText.escape(text)
        return expandTokens(text) { token -> PlaceholderAPI.setPlaceholders(player, token) }
    }

    companion object {
        private val TOKEN = Regex("%[^%\\s]+%")
        private val MM = MiniMessage.miniMessage()
        private const val SENTINEL = "\u0001"

        /**
         * Player text escaped verbatim, each resolved token replaced by its value. Values go through the
         * legacy serializer only — colours and decorations, never click/hover — then back out as
         * MiniMessage, so a value can't inject tags. Unresolved tokens stay literal.
         */
        @JvmStatic
        fun expandTokens(text: String, resolve: (String) -> String): String {
            val out = StringBuilder()
            var last = 0
            for (m in TOKEN.findAll(text)) {
                out.append(PlayerText.escape(text.substring(last, m.range.first)))
                val value = resolve(m.value)
                out.append(if (value == m.value) PlayerText.escape(value) else closedMiniMessage(value))
                last = m.range.last + 1
            }
            out.append(PlayerText.escape(text.substring(last)))
            return out.toString()
        }

        // The serializer leaves a trailing colour open, which would bleed into the player's text after
        // the value; a sibling after it forces every tag closed, then the sibling is cut off again.
        private fun closedMiniMessage(legacyValue: String): String {
            val value = PrefixFormatter.format(legacyValue)
            val serialized = MM.serialize(Component.text().append(value).append(Component.text(SENTINEL)).build())
            return serialized.removeSuffix(SENTINEL)
        }
    }
}
