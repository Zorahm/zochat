package zorahm.zochat.chat

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zorahm.zochat.Fakes

class ItemPlaceholderTest {

    private val format = "<blue>[{item} x{amount}]</blue>"

    @Test
    fun anvilRenamedTagsAreNotParsed() {
        val name = "<click:run_command:'/op Hacker'>Sword"
        val component = MiniMessage.miniMessage().deserialize(PlaceholderService.formatItem(format, name, 1))

        assertEquals("[$name x1]", PlainTextComponentSerializer.plainText().serialize(component))
        assertTrue(Fakes.leaves(component).all { it.second.clickEvent() == null }, "no click event may survive")
    }

    @Test
    fun plainNameAndAmountAreSubstituted() {
        assertEquals("<blue>[diamond sword x3]</blue>", PlaceholderService.formatItem(format, "diamond sword", 3))
    }
}
