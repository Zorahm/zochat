package zorahm.zochat.advancement

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import zorahm.zochat.chat.PapiHook
import zorahm.zochat.config.ChatConfig

/**
 * Restyles advancement announcements. The advancement is inserted as Paper's own display name
 * component, never flattened to a string: vanilla sends title and description as translatable
 * components that each CLIENT resolves in its own language, and the hover lives on that component —
 * serializing it would pin the server's language and drop the hover.
 */
class AdvancementListener(
    private val config: AdvancementConfig,
    private val chatConfig: ChatConfig,
    private val papi: PapiHook,
) : Listener {

    // HIGH so plugins at the default priority see the vanilla message, and MONITOR listeners see ours.
    @EventHandler(priority = EventPriority.HIGH)
    fun onAdvancement(event: PlayerAdvancementDoneEvent) {
        if (!config.isEnabled) return
        // null = vanilla wouldn't announce it (hidden, recipe, announce_to_chat=false or the gamerule is off).
        event.message() ?: return
        val display = event.advancement.display ?: return

        var format = config.format(display.frame().name)
        if (format.isBlank()) {
            event.message(null)
            return
        }
        if (chatConfig.isPlaceholderApiEnabled && chatConfig.isPlaceholderApiFormatEnabled) {
            format = papi.apply(event.player, format)
        }
        // Replacing the event's message (rather than cancelling and broadcasting) keeps vanilla's delivery:
        // every player plus the console, and other plugins still see the final line.
        event.message(render(format, event.player.name(), event.advancement.displayName()))
    }

    companion object {
        private val MM = MiniMessage.miniMessage()

        /**
         * {player}/{advancement} become inserted-component tags, so they keep their hover and translation,
         * survive a surrounding <gradient>, and work as <lang:...> arguments.
         */
        @JvmStatic
        fun render(format: String, player: Component, advancement: Component): Component =
            MM.deserialize(
                format.replace("{player}", "<zochat_player>").replace("{advancement}", "<zochat_advancement>"),
                TagResolver.resolver(
                    Placeholder.component("zochat_player", player),
                    Placeholder.component("zochat_advancement", advancement),
                ),
            )
    }
}
