package zorahm.zochat.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PapiPlayerTextTest {

    private val values = mapOf(
        "%player_name%" to "Steve",
        "%vault_prefix%" to "&c[Admin]",
        "%evil%" to "<click:run_command:'/op x'>hi",
    )

    private fun rendered(escapedInput: String): String {
        val expanded = PapiHook.expandEscaped(escapedInput) { values[it] ?: it }
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(expanded))
    }

    @Test
    fun knownPlaceholderIsExpanded() {
        assertEquals("hi Steve", rendered("hi %player_name%"))
    }

    // The whole message used to go through legacy->MiniMessage conversion before being escaped,
    // so a player's own "&c" showed up as a literal "<red>".
    @Test
    fun playersOwnLegacyCodesStayLiteral() {
        assertEquals("&chello Steve", rendered("&chello %player_name%"))
    }

    @Test
    fun colourCodesInValuesAreStripped() {
        assertEquals("[Admin] says", rendered("%vault_prefix% says"))
    }

    @Test
    fun tagsInValuesAreNotParsed() {
        assertEquals("<click:run_command:'/op x'>hi", rendered("%evil%"))
    }

    @Test
    fun unknownPlaceholdersAndStrayPercentsStayLiteral() {
        assertEquals("%nope% 100% sure", rendered("%nope% 100% sure"))
    }

    // Custom ^ placeholders: the admin picks the PAPI value, but its content can be player-made
    // (nicknames, town names) — keep legacy colours, never parse tags from it.
    @Test
    fun colouredValueKeepsLegacyColour() {
        val component = MiniMessage.miniMessage().deserialize(PapiHook.expandColored("Rank %vault_prefix%") { values[it] ?: it })
        assertEquals("Rank [Admin]", PlainTextComponentSerializer.plainText().serialize(component))
        assertEquals(NamedTextColor.RED, colorOf(component, "[Admin]"))
    }

    @Test
    fun colouredValueCannotCarryTags() {
        val expanded = PapiHook.expandColored("%evil%") { values[it] ?: it }
        val component = MiniMessage.miniMessage().deserialize(expanded)
        assertEquals("<click:run_command:'/op x'>hi", PlainTextComponentSerializer.plainText().serialize(component))
    }

    @Test
    fun adminTextAroundColouredValuesKeepsItsTags() {
        val component = MiniMessage.miniMessage().deserialize(PapiHook.expandColored("<green>%player_name%") { values[it] ?: it })
        assertEquals(NamedTextColor.GREEN, colorOf(component, "Steve"))
    }

    private fun colorOf(c: Component, text: String, inherited: TextColor? = null): TextColor? {
        val current = c.color() ?: inherited
        if (c is TextComponent && c.content().contains(text)) return current
        for (child in c.children()) {
            colorOf(child, text, current)?.let { return it }
        }
        return null
    }
}
