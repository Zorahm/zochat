package zorahm.zochat.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

// The chat line exactly as ChatService assembles it — the path issue #3 was about (LuckPerms legacy
// colour codes shown as raw characters next to the nickname).
class ChatLineTest {

    private val format = "<#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}"

    private fun line(format: String, prefix: String, suffix: String): Component = ChatService.assemble(
        ChatService.inlineMeta(format, prefix, suffix),
        Component.text("Steve"),
        Component.text("hi"),
        TagResolver.empty(),
    )

    private fun plain(c: Component) = PlainTextComponentSerializer.plainText().serialize(c)

    private fun colorOf(c: Component, text: String, inherited: TextColor? = null): TextColor? {
        val current = c.color() ?: inherited
        if (c is TextComponent && c.content().contains(text)) return current
        for (child in c.children()) colorOf(child, text, current)?.let { return it }
        return null
    }

    @Test
    fun ampersandCodesInAPrefixAreColoursNotText() {
        val c = line(format, "&c[Admin] &f", "")
        assertEquals("[Admin] Steve › hi", plain(c))
        assertEquals(NamedTextColor.RED, colorOf(c, "[Admin]"))
        assertEquals(NamedTextColor.WHITE, colorOf(c, "Steve"))
    }

    @Test
    fun sectionHexCodesInAPrefixAreColoursNotText() {
        val c = line(format, "§x§f§f§5§5§5§5[VIP] ", "")
        assertFalse(plain(c).contains('§'), plain(c))
        assertEquals(TextColor.color(0xff5555), colorOf(c, "[VIP]"))
    }

    @Test
    fun hexSuffixColoursTheMessage() {
        val c = line(format, "", "&#55ff55")
        assertEquals("Steve › hi", plain(c))
        assertEquals(TextColor.color(0x55ff55), colorOf(c, "hi"))
    }

    // The format and meta from the console log in issue #2: MiniMessage meta must keep working too.
    @Test
    fun miniMessageMetaFromTheIssueRendersCleanly() {
        val c = line(
            "<#008fcc>Ⓛ <#c0c0c0>>> <#fcfcfc>{prefix}{player}{suffix}<#c0c0c0>: </#c0c0c0>{message}",
            "<gradient:#00fff0:#008799>Актив</gradient> <#c0c0c0>| ",
            " <#e60e00>[СПМ]",
        )
        assertEquals("Ⓛ >> Актив | Steve [СПМ]: hi", plain(c))
    }
}
