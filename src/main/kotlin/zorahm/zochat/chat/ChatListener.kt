package zorahm.zochat.chat

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatListener(
    private val plugin: Plugin,
    private val chatService: ChatService,
    private val config: ChatConfig,
    private val messages: Messages
) : Listener {

    private val plain: PlainTextComponentSerializer = PlainTextComponentSerializer.plainText()

    // Set only for the duration of a synchronous Player.chat() made by chatAs().
    private val forced = ConcurrentHashMap<UUID, Forced>()

    /** What a /g, /l or /bubble command asked for when it re-sent its text as chat. */
    enum class Forced { LOCAL, GLOBAL, BUBBLE }

    sealed interface Route {
        data class Send(val channel: ChatChannel, val text: String) : Route

        data class Bubble(val text: String) : Route

        /** Both channels are off: zoChat stays out of the way and vanilla chat handles it. */
        data object Vanilla : Route

        /** "!" with nothing after it — swallowed instead of broadcasting an empty line. */
        data object Empty : Route

        data object GlobalDisabled : Route

        data object LocalDisabled : Route
    }

    /**
     * Re-sends a command's text as the player's own chat. Mute, anti-spam and chat-logging plugins
     * only watch the chat event, so /g, /l and /bubble used to let muted players talk. Player.chat()
     * fires AsyncChatEvent synchronously on this thread, so [onChat] still sees the [forced] entry.
     */
    fun chatAs(player: Player, text: String, intent: Forced) {
        forced[player.uniqueId] = intent
        try {
            // Player.chat() runs a leading '/' as a command; route() trims the space off again.
            player.chat(if (text.startsWith("/")) " $text" else text)
        } finally {
            forced.remove(player.uniqueId)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        // Only a synchronous event can be chatAs()'s — a real chat packet from the same player
        // arrives async and must not pick up the command's channel.
        val intent = if (event.isAsynchronous) null else forced[event.player.uniqueId]
        val route = route(plain.serialize(event.message()), config.isLocalChatEnabled, config.isGlobalChatEnabled, intent)
        if (route == Route.Vanilla) return
        event.isCancelled = true
        val sender = event.player
        when (route) {
            Route.GlobalDisabled -> sender.sendMessage(messages.component("chat.global-disabled"))
            Route.LocalDisabled -> sender.sendMessage(messages.component("chat.local-disabled"))
            // AsyncChatEvent usually fires off the main thread, but the send pipeline reads player
            // state (inventory, health, location) and plays sounds — main-thread-only work. Hop back on.
            // Explicit Runnable: runTask is overloaded on Runnable and Consumer<BukkitTask>, so a bare
            // trailing lambda is ambiguous to the Kotlin resolver.
            is Route.Send -> Bukkit.getScheduler().runTask(plugin, Runnable { chatService.send(sender, route.text, route.channel) })
            is Route.Bubble -> Bukkit.getScheduler().runTask(plugin, Runnable { chatService.sendBubble(sender, route.text) })
            Route.Empty, Route.Vanilla -> {}
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun route(raw: String, localEnabled: Boolean, globalEnabled: Boolean, forced: Forced? = null): Route {
            if (forced != null) {
                val text = raw.trim()
                return when {
                    text.isEmpty() -> Route.Empty
                    forced == Forced.BUBBLE -> Route.Bubble(text)
                    forced == Forced.LOCAL && !localEnabled -> Route.LocalDisabled
                    forced == Forced.GLOBAL && !globalEnabled -> Route.GlobalDisabled
                    forced == Forced.LOCAL -> Route.Send(ChatChannel.LOCAL, text)
                    else -> Route.Send(ChatChannel.GLOBAL, text)
                }
            }
            if (!localEnabled && !globalEnabled) return Route.Vanilla
            if (raw.startsWith("!")) {
                val text = raw.substring(1).trim()
                if (text.isEmpty()) return Route.Empty
                if (!globalEnabled) return Route.GlobalDisabled
                return Route.Send(ChatChannel.GLOBAL, text)
            }
            if (raw.isBlank()) return Route.Empty
            return Route.Send(if (localEnabled) ChatChannel.LOCAL else ChatChannel.GLOBAL, raw)
        }
    }
}
