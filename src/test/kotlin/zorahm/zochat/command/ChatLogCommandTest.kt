package zorahm.zochat.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zorahm.zochat.Fakes

class ChatLogCommandTest {

    private val template = "<gray>• {message}</gray>"

    @Test
    fun loggedTagsAreShownLiterallyNotParsed() {
        val raw = "<click:run_command:'/op Hacker'>free diamonds"
        val line = ChatLogCommand.renderLine(template, raw)

        assertEquals("• $raw", PlainTextComponentSerializer.plainText().serialize(line))
        assertTrue(Fakes.leaves(line).all { it.second.clickEvent() == null }, "no click event may survive")
    }

    @Test
    fun templateFormattingStillApplies() {
        val line: Component = ChatLogCommand.renderLine(template, "hello")
        assertTrue(Fakes.leaves(line).all { it.second.color() == NamedTextColor.GRAY })
    }
}
