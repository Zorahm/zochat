package zorahm.zochat.chat

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import zorahm.zochat.Fakes

class LuckPermsMetaTest {

    private val meta = mapOf("clan" to "&b[Wolves]", "chat-tag" to "<gold>★</gold>", "price" to "$1 \\o/")
    private fun expand(format: String) = LuckPermsMeta.expand(format) { meta[it] }

    @Test
    fun resolvesAnyNumberOfKeys() {
        assertEquals("<aqua>[Wolves] <gold>★</gold> {player}", expand("{meta:clan} {meta:chat-tag} {player}"))
    }

    @Test
    fun missingKeyBecomesEmpty() {
        assertEquals("[] {player}", expand("[{meta:nope}] {player}"))
    }

    @Test
    fun valuesAreInsertedLiterally() {
        assertEquals("cost: $1 \\o/", expand("cost: {meta:price}"))
    }

    @Test
    fun formatsWithoutTokensAreUntouched() {
        assertEquals("{meta:} {meta} plain", expand("{meta:} {meta} plain"))
    }

    @Test
    fun legacyValueRendersAndItsOpenColourFlowsOn() {
        val c = MiniMessage.miniMessage().deserialize(expand("{meta:clan} Steve"))
        assertEquals("[Wolves] Steve", PlainTextComponentSerializer.plainText().serialize(c))
        // Like a prefix: an unclosed legacy colour carries over to the text after it.
        assertEquals(NamedTextColor.AQUA, Fakes.leaves(c).single { it.first.contains("Steve") }.second.color())
    }
}
