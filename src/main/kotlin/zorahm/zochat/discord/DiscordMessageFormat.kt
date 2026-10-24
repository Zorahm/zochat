package zorahm.zochat.discord

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.regex.Pattern

/** Renders a Discord message for Minecraft with zoChat's `formats.discord` (pure, no DiscordSRV types). */
object DiscordMessageFormat {

    /** A Discord role; [color] is null for a role without a colour. */
    data class Role(val name: String, val color: Int?)

    private val MINI: MiniMessage = MiniMessage.miniMessage()
    private val TOKEN: Pattern = Pattern.compile("\\{(role|name|message)}")

    /**
     * The message text as a plain component (Discord users can't inject tags) followed by each
     * attachment as a clickable `[file name]`, cut at [maxLength] characters.
     */
    @JvmStatic
    fun body(text: String, attachments: List<Pair<String, String>>, maxLength: Int): Component {
        val cut = if (text.length > maxLength) text.take(maxLength) + "…" else text
        val parts = ArrayList<Component>()
        if (cut.isNotBlank()) parts += Component.text(cut)
        for ((fileName, url) in attachments) {
            parts += Component.text("[$fileName]")
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url)))
        }
        return Component.join(JoinConfiguration.separator(Component.space()), parts)
    }

    @JvmStatic
    fun render(format: String, name: String, role: Role?, body: Component): Component {
        val roleComponent = role?.let { Component.text(it.name + " ").color(it.color?.let(TextColor::color)) } ?: Component.empty()
        // One pass over all tokens: replacing them one after another would also replace a "{message}"
        // that someone put in their Discord name.
        return MINI.deserialize(format).replaceText { config ->
            config.match(TOKEN).replacement { match, _ ->
                when (match.group(1)) {
                    "role" -> roleComponent
                    "name" -> Component.text(name)
                    else -> body
                }
            }
        }
    }
}
