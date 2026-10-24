package zorahm.zochat.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrefixFormatterTest {

    @Test
    fun miniMessageLeavesLegacyCodesRaw() {
        val shownByOldCode = PLAIN.serialize(MiniMessage.miniMessage().deserialize("&c[Admin] "))
        assertEquals("&c[Admin] ", shownByOldCode)
    }

    @Test
    fun ampersandColorIsParsed() {
        val result = PrefixFormatter.format("&c[Admin] ")
        assertEquals("[Admin] ", PLAIN.serialize(result))
        assertTrue(PrefixFormatter.toMiniMessage("&c[Admin] ").contains("<red>"))
    }

    @Test
    fun sectionColorIsParsed() {
        val result = PrefixFormatter.format("§c[Admin]")
        assertEquals("[Admin]", PLAIN.serialize(result))
        assertTrue(PrefixFormatter.toMiniMessage("§c[Admin]").contains("<red>"))
    }

    // Check the actual parsed color, not the MiniMessage serialization string
    // (serialization may collapse hex to named color, etc.).
    @Test
    fun ampersandHexIsParsed() {
        val result = PrefixFormatter.format("&#a1b2c3[VIP]")
        assertEquals("[VIP]", PLAIN.serialize(result))
        assertEquals(TextColor.color(0xa1b2c3), firstColor(result))
    }

    @Test
    fun sectionUnusualHexFormatIsParsed() {
        val result = PrefixFormatter.format("§x§a§1§b§2§c§3[VIP]")
        assertEquals("[VIP]", PLAIN.serialize(result))
        assertEquals(TextColor.color(0xa1b2c3), firstColor(result))
    }

    @Test
    fun emptyAndNullAreSafe() {
        assertEquals(Component.empty(), PrefixFormatter.format(""))
        assertEquals(Component.empty(), PrefixFormatter.format(null))
        assertEquals("", PrefixFormatter.toMiniMessage(""))
        assertEquals("", PrefixFormatter.toMiniMessage(null))
    }

    @Test
    fun toMiniMessageStripsLegacyCodes() {
        val mm = PrefixFormatter.toMiniMessage("&c[Admin] ")
        assertFalse(mm.contains("&c"))
        assertEquals("[Admin] ", PLAIN.serialize(MiniMessage.miniMessage().deserialize(mm)))
    }

    @Test
    fun toMiniMessagePreservesExistingMiniMessageTags() {
        assertEquals("<#ff5555>", PrefixFormatter.toMiniMessage("<#ff5555>"))
        assertEquals(
            "<gradient:#55ff55:#aaffaa>x</gradient>",
            PrefixFormatter.toMiniMessage("<gradient:#55ff55:#aaffaa>x</gradient>")
        )
    }

    @Test
    fun toMiniMessageTranslatesLegacyColors() {
        assertEquals("<red>Hi", PrefixFormatter.toMiniMessage("&cHi"))
        assertEquals("<red>Hi", PrefixFormatter.toMiniMessage("§cHi"))
        assertEquals("<#ff5555>X", PrefixFormatter.toMiniMessage("&#ff5555X"))
        assertEquals("<#ff5555>Y", PrefixFormatter.toMiniMessage("§x§f§f§5§5§5§5Y"))
    }

    @Test
    fun toMiniMessageHandlesMixedLegacyAndMiniMessage() {
        assertEquals("<red><bold>Z", PrefixFormatter.toMiniMessage("&c<bold>Z"))
    }

    // Verifies the ChatService assembly: an unclosed colour inlined before a replaced
    // {message} component flows into that component (so a <#hex> suffix recolours the message).
    @Test
    fun suffixColourFlowsIntoReplacedMessage() {
        val base = MiniMessage.miniMessage().deserialize("<#a1b2c3>{message}")
        val result = base.replaceText { it.matchLiteral("{message}").replacement(Component.text("hello")) }
        assertEquals(TextColor.color(0xa1b2c3), effectiveColor(result, "hello"))
    }

    // Legacy semantics: a colour code ends bold/italic/etc. MiniMessage would instead nest <gray>
    // inside <bold>, so the common "&c&l[Admin]&7 " prefix made the name and message bold too.
    @Test
    fun legacyColourAfterDecorationEndsTheDecoration() {
        val rendered = MiniMessage.miniMessage().deserialize(PrefixFormatter.toMiniMessage("&c&l[Admin]&7 ") + "Steve")
        assertEquals(true, effectiveBold(rendered, "[Admin]"))
        assertEquals(false, effectiveBold(rendered, "Steve"))
    }

    @Test
    fun legacyColourAfterBoldCodeResetsIt() {
        val rendered = MiniMessage.miniMessage().deserialize(PrefixFormatter.toMiniMessage("&l&cX"))
        assertEquals(false, effectiveBold(rendered, "X"))
    }

    // Only decorations opened by legacy codes in the same string are ended — bold coming from the
    // surrounding chat format is the admin's choice and must survive a coloured prefix.
    @Test
    fun legacyColourKeepsDecorationsFromTheSurroundingFormat() {
        val rendered = MiniMessage.miniMessage().deserialize("<bold>" + PrefixFormatter.toMiniMessage("&cX"))
        assertEquals(true, effectiveBold(rendered, "X"))
    }

    private fun effectiveBold(c: Component, text: String, inherited: Boolean = false): Boolean? {
        val current = when (c.decoration(TextDecoration.BOLD)) {
            TextDecoration.State.TRUE -> true
            TextDecoration.State.FALSE -> false
            else -> inherited
        }
        if (c is TextComponent && c.content().contains(text)) {
            return current
        }
        for (child in c.children()) {
            val found = effectiveBold(child, text, current)
            if (found != null) {
                return found
            }
        }
        return null
    }

    private fun firstColor(c: Component): TextColor? {
        c.color()?.let { return it }
        for (child in c.children()) {
            val found = firstColor(child)
            if (found != null) {
                return found
            }
        }
        return null
    }

    private fun effectiveColor(c: Component, text: String, inherited: TextColor? = null): TextColor? {
        val current = c.color() ?: inherited
        if (c is TextComponent && c.content().contains(text)) {
            return current
        }
        for (child in c.children()) {
            val found = effectiveColor(child, text, current)
            if (found != null) {
                return found
            }
        }
        return null
    }

    private companion object {
        val PLAIN: PlainTextComponentSerializer = PlainTextComponentSerializer.plainText()
    }
}
