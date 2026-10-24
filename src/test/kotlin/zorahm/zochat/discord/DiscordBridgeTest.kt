package zorahm.zochat.discord

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import zorahm.zochat.chat.ChatChannel

class DiscordBridgeTest {

    private val settings = DiscordBridge.Settings(globalChannel = "global", localChat = false, localChannel = "local")
    private val format = "<#5865F2>[Discord]</#5865F2> {role}<white>{name}</white> › {message}"

    private fun plain(c: Component) = PlainTextComponentSerializer.plainText().serialize(c)

    private fun find(c: Component, text: String): TextComponent? {
        if (c is TextComponent && c.content().contains(text)) return c
        return c.children().firstNotNullOfOrNull { find(it, text) }
    }

    private fun colorOf(c: Component, text: String, inherited: TextColor? = null): TextColor? {
        val current = c.color() ?: inherited
        if (c is TextComponent && c.content().contains(text)) return current
        return c.children().firstNotNullOfOrNull { colorOf(it, text, current) }
    }

    @Test
    fun globalChatGoesToTheGlobalGameChannel() {
        assertEquals("global", DiscordBridge.gameChannel(ChatChannel.GLOBAL, settings))
    }

    // Issue #1 asked for a switch: local chat reaches Discord only when it's turned on.
    @Test
    fun localChatStaysInGameUnlessEnabled() {
        assertNull(DiscordBridge.gameChannel(ChatChannel.LOCAL, settings))
        assertEquals("local", DiscordBridge.gameChannel(ChatChannel.LOCAL, settings.copy(localChat = true)))
    }

    // Discord names and messages are typed by anyone on the Discord server.
    @Test
    fun discordNamesAndTextAreNeverParsedAsMiniMessage() {
        val body = DiscordMessageFormat.body("<click:run_command:'/op x'>hi", emptyList(), 256)
        val line = DiscordMessageFormat.render(format, "<red>Evil", null, body)
        assertEquals("[Discord] <red>Evil › <click:run_command:'/op x'>hi", plain(line))
    }

    @Test
    fun topRoleIsShownInItsColour() {
        val line = DiscordMessageFormat.render(
            format, "Steve", DiscordMessageFormat.Role("Admin", 0xE53935), DiscordMessageFormat.body("hi", emptyList(), 256)
        )
        assertEquals("[Discord] Admin Steve › hi", plain(line))
        assertEquals(TextColor.color(0xE53935), colorOf(line, "Admin"))
    }

    @Test
    fun attachmentsBecomeClickableLinks() {
        val body = DiscordMessageFormat.body("look", listOf("cat.png" to "https://cdn.example/cat.png"), 256)
        assertEquals("look [cat.png]", plain(body))
        assertEquals(ClickEvent.openUrl("https://cdn.example/cat.png"), find(body, "[cat.png]")!!.clickEvent())
    }

    // DiscordSRV truncates on its own path; zoChat takes the message over, so it has to as well.
    @Test
    fun longMessagesAreCut() {
        val text = plain(DiscordMessageFormat.body("a".repeat(300), emptyList(), 256))
        assertEquals("a".repeat(256) + "…", text)
    }
}
