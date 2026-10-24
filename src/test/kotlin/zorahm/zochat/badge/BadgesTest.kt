package zorahm.zochat.badge

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class BadgesTest {

    // The table the generator wrote into the plugin's resources — the same one the jar ships.
    private val table = BadgeTable.read(InputStreamReader(javaClass.classLoader.getResourceAsStream("badges.yml")!!, Charsets.UTF_8))

    private var settings = Badges.Settings(enabled = true, russian = false, icons = true)
    private val badges = Badges(table) { settings }
    private val mm = MiniMessage.miniMessage()
    private val plain = PlainTextComponentSerializer.plainText()

    private fun glyphOf(c: Component): TextComponent =
        if (c is TextComponent && c.style().font() != null) c else c.children().firstNotNullOf { runCatching { glyphOf(it) }.getOrNull() }

    @Test
    fun consoleBadgeIsAGlyphFromThePackFont() {
        val glyph = glyphOf(mm.deserialize("<badge:console>", badges.forPlayers))
        assertEquals("", glyph.content())
        assertEquals(Key.key("zochat:badges"), glyph.style().font())
    }

    // Bitmap glyphs are multiplied by the text colour and get chat's drop shadow; a badge placed
    // after "<red>" or inside "<bold>" must still look exactly like the PNG.
    @Test
    fun glyphIgnoresSurroundingColourShadowAndDecorations() {
        val glyph = glyphOf(mm.deserialize("<red><bold><badge:admin>", badges.forPlayers))
        assertEquals(NamedTextColor.WHITE, glyph.color())
        assertEquals(ShadowColor.none(), glyph.shadowColor())
        assertEquals(TextDecoration.State.FALSE, glyph.decoration(TextDecoration.BOLD))
    }

    @Test
    fun languageAndIconSettingsPickTheVariant() {
        settings = Badges.Settings(enabled = true, russian = true, icons = false)
        assertEquals("", glyphOf(mm.deserialize("<badge:console>", badges.forPlayers)).content())
    }

    @Test
    fun explicitVariantWins() {
        assertEquals("", glyphOf(mm.deserialize("<badge:admin:ru>", badges.forPlayers)).content())
    }

    // The console has no resource pack: a glyph would print as a private-use box in the log.
    @Test
    fun consoleGetsTheTextLabel() {
        assertEquals("[CONSOLE] Server", plain.serialize(mm.deserialize("<badge:console> Server", badges.forConsole)))
        settings = Badges.Settings(enabled = true, russian = true, icons = true)
        assertEquals("[КОНСОЛЬ]", plain.serialize(mm.deserialize("<badge:console>", badges.forConsole)))
    }

    @Test
    fun disabledBadgesFallBackToLabelsForPlayersToo() {
        settings = Badges.Settings(enabled = false, russian = false, icons = true)
        assertEquals("[ADMIN]", plain.serialize(mm.deserialize("<badge:admin>", badges.forPlayers)))
    }

    @Test
    fun unknownBadgeStaysLiteral() {
        assertEquals("<badge:nope>", plain.serialize(mm.deserialize("<badge:nope>", badges.forPlayers)))
    }
}
