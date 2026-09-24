package zorahm.zochat.chat

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zorahm.zochat.Fakes

class PapiHookTest {

    private val values = mapOf("%vault_prefix%" to "§6[VIP]", "%evil%" to "<click:run_command:'/op x'>boom")
    private fun render(text: String) =
        MiniMessage.miniMessage().deserialize(PapiHook.expandTokens(text) { values[it] ?: it })

    @Test
    fun playerTextAroundTokensStaysLiteral() {
        val c = render("&rock <b>%vault_prefix% \\")
        assertEquals("&rock <b>[VIP] \\", PlainTextComponentSerializer.plainText().serialize(c))
        assertTrue(Fakes.leaves(c).none { it.second.hasDecoration(TextDecoration.BOLD) })
    }

    @Test
    fun legacyColourOfValueRendersAndDoesNotBleed() {
        val leaves = Fakes.leaves(render("%vault_prefix% hello"))
        assertEquals(NamedTextColor.GOLD, leaves.single { it.first.contains("[VIP]") }.second.color())
        assertEquals(null, leaves.single { it.first.contains("hello") }.second.color())
    }

    @Test
    fun valueCannotInjectTags() {
        val c = render("%evil%")
        assertEquals("<click:run_command:'/op x'>boom", PlainTextComponentSerializer.plainText().serialize(c))
        assertTrue(Fakes.leaves(c).all { it.second.clickEvent() == null })
    }

    @Test
    fun unresolvedTokensAndPercentSignsStayLiteral() {
        assertEquals(
            "50% off %unknown_token%",
            PlainTextComponentSerializer.plainText().serialize(render("50% off %unknown_token%"))
        )
    }
}
