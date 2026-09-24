package zorahm.zochat.util

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Assertions.assertEquals
import zorahm.zochat.Fakes

class PlayerTextTest {

    private val mm = MiniMessage.miniMessage()

    @ParameterizedTest
    @ValueSource(strings = ["hey \\", "a\\b", "x \\\\", "\\<red>t", "c:\\path\\", "\\\\<red>q", "<click:run_command:/op x>hi"])
    fun rendersExactlyAsTypedAndKeepsTheFollowingTrustedTag(typed: String) {
        // The escaped text is followed by a trusted tag, as when a mention highlight is spliced in.
        val component = mm.deserialize(PlayerText.escape(typed) + "<yellow>@Steve</yellow>")

        assertEquals("$typed@Steve", PlainTextComponentSerializer.plainText().serialize(component))
        assertEquals(NamedTextColor.YELLOW, Fakes.leaves(component).single { it.first == "@Steve" }.second.color())
    }
}
