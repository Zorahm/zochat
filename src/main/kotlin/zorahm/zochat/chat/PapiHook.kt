package zorahm.zochat.chat

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Isolates every PlaceholderAPI reference behind one class so the rest of the plugin runs
 * fine when PAPI isn't installed. The me.clip bytecode lives only in [apply]'s available
 * branch, guarded by [isAvailable] — so the JVM never resolves the PlaceholderAPI class
 * (no NoClassDefFoundError) on servers without the plugin.
 */
class PapiHook {

    // PAPI is a softdepend, so it's enabled before zoChat loads — a one-time check is reliable.
    val isAvailable: Boolean = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")

    fun apply(player: Player, text: String): String {
        if (!isAvailable || text.isEmpty()) return text
        // Placeholders like %vault_prefix% commonly return legacy colour codes that MiniMessage
        // can't parse — convert to MiniMessage tags first (issue #3), leaving any MiniMessage
        // already in the text untouched.
        return PrefixFormatter.toMiniMessage(PlaceholderAPI.setPlaceholders(player, text))
    }
}
