package zorahm.zochat.chat

import net.luckperms.api.LuckPerms
import org.bukkit.entity.Player
import java.util.function.Function

/**
 * Resolves `{meta:<key>}` tokens in admin-authored formats to the player's LuckPerms meta values
 * (`/lp group vip meta set clan "&b[Wolves]"` -> `{meta:clan}`), so any number of per-group/per-player
 * tags work without PlaceholderAPI. Only admins can set meta, so values are trusted like prefixes.
 */
class LuckPermsMeta(private val luckPerms: LuckPerms) {

    fun expand(player: Player, format: String): String {
        if (!format.contains(TOKEN_START)) return format
        val meta = luckPerms.userManager.getUser(player.uniqueId)?.cachedData?.metaData
        return LuckPermsMeta.expand(format) { key -> meta?.getMetaValue(key) }
    }

    companion object {
        private const val TOKEN_START = "{meta:"
        private val TOKEN = Regex("\\{meta:([^{}\\s]+)}")

        /**
         * Replaces every `{meta:<key>}` with [lookup]'s value (missing key -> empty). Values go through
         * [PrefixFormatter.toMiniMessage] like prefixes do: meta is often set with legacy `&` codes, which
         * MiniMessage would print raw (issue #3), and an unclosed colour flows into the following text.
         */
        @JvmStatic
        fun expand(format: String, lookup: Function<String, String?>): String {
            if (!format.contains(TOKEN_START)) return format
            // The lambda form of replace() inserts results literally, so '$' or '\' in a value is safe.
            return TOKEN.replace(format) { m -> PrefixFormatter.toMiniMessage(lookup.apply(m.groupValues[1]) ?: "") }
        }
    }
}
