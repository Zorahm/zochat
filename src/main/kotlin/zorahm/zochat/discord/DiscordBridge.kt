package zorahm.zochat.discord

import org.bukkit.entity.Player
import zorahm.zochat.chat.ChatChannel

/**
 * What the chat pipeline needs from a Discord bridge. Free of DiscordSRV types, so the plugin loads
 * and runs without it; [DiscordSrvHook] is the only implementation that touches DiscordSRV.
 */
interface DiscordBridge {

    /** Hands a chat message that was just shown in game to Discord. */
    fun relay(player: Player, channel: ChatChannel, message: String)

    fun close()

    data class Settings(val globalChannel: String, val localChat: Boolean, val localChannel: String)

    /** DiscordSRV isn't installed. */
    object None : DiscordBridge {
        override fun relay(player: Player, channel: ChatChannel, message: String) {}
        override fun close() {}
    }

    companion object {
        /** The DiscordSRV game channel a zoChat channel goes to, or null when it stays in game. */
        @JvmStatic
        fun gameChannel(channel: ChatChannel, settings: Settings): String? = when (channel) {
            ChatChannel.GLOBAL -> settings.globalChannel
            ChatChannel.LOCAL -> settings.localChannel.takeIf { settings.localChat }
        }
    }
}
