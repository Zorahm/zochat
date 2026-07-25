package zorahm.zochat.chat

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import zorahm.zochat.config.ChatConfig

/**
 * Exposes zoChat data to other plugins via %zochat_...%. PAPI requests run on the main thread
 * and must return fast, so only synchronous, no-DB values are offered here.
 */
class ZoChatExpansion(
    private val plugin: JavaPlugin,
    private val config: ChatConfig,
    private val cooldowns: CooldownService,
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "zochat"

    override fun getAuthor(): String = plugin.pluginMeta.authors.firstOrNull() ?: "ZorahM"

    override fun getVersion(): String = plugin.pluginMeta.version

    // We register this ourselves rather than via the eCloud, so it must survive a PAPI reload.
    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? = when (params.lowercase()) {
        "version" -> plugin.pluginMeta.version
        "local_radius" -> config.localChatRadius.toString()
        "here_radius" -> config.mentionHereRadius.toString()
        "cooldown_local" -> cooldowns.remainingSeconds(
            player?.uniqueId, CooldownService.Channel.LOCAL, config.localChatCooldown
        ).toString()
        "cooldown_global" -> cooldowns.remainingSeconds(
            player?.uniqueId, CooldownService.Channel.GLOBAL, config.globalChatCooldown
        ).toString()
        else -> null
    }
}
