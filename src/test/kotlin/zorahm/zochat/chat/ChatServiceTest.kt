package zorahm.zochat.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zorahm.zochat.Fakes

class ChatServiceTest {

    private val plain = PlainTextComponentSerializer.plainText()
    private val click = ClickEvent.suggestCommand("/msg Steve ")
    private val player = Component.text("Steve").clickEvent(click)
    private val message = Component.text("hi ").append(Component.text("@Bob", NamedTextColor.YELLOW))

    @Test
    fun unclosedGradientFromSuffixStillResolvesTokens() {
        // What ChatService gets after inlining a LuckPerms suffix like "<gradient:#ff0000:#0000ff>[VIP] ".
        val rendered = ChatService.render("<gradient:#ff0000:#0000ff>[VIP] {player} › {message}", player, message)

        assertEquals("[VIP] Steve › hi @Bob", plain.serialize(rendered))
        val leaves = Fakes.leaves(rendered)
        val steve = leaves.filter { it.first in listOf("S", "t", "e", "v") && it.second.clickEvent() != null }
        assertEquals(5, steve.size, "every character of the name keeps its click event: $leaves")
        assertTrue(steve.all { it.second.clickEvent() == click && it.second.color() != null })
        assertEquals(NamedTextColor.YELLOW, leaves.single { it.first == "@Bob" }.second.color())
    }

    @Test
    fun openColourFromPrefixFlowsIntoMessage() {
        val red = TextColor.fromHexString("#ff5555")
        val rendered = ChatService.render("<#ff5555>[VIP] {player}<gray>: </gray>{message}", player, message)

        assertEquals("[VIP] Steve: hi @Bob", plain.serialize(rendered))
        val leaves = Fakes.leaves(rendered).toMap()
        assertEquals(red, leaves["Steve"]!!.color())
        assertEquals(click, leaves["Steve"]!!.clickEvent())
        assertEquals(red, leaves["hi "]!!.color())
    }

    @Test
    fun localRecipientsAreSameWorldWithinRadius() {
        val overworld = Fakes.world("world")
        val nether = Fakes.world("world_nether")
        val sender = Fakes.player("Sender", overworld)
        val near = Fakes.player("Near", overworld, x = 10.0)
        val edge = Fakes.player("Edge", overworld, x = 50.0)
        val far = Fakes.player("Far", overworld, x = 51.0)
        val otherWorld = Fakes.player("Nether", nether)

        val recipients = ChatService.localRecipients(sender, listOf(sender, near, edge, far, otherWorld), 50)

        assertEquals(listOf(sender, near, edge), recipients)
    }

    @Test
    fun mentionsOnlyReachPlayersWhoReceiveTheMessage() {
        val overworld = Fakes.world("world")
        val sender = Fakes.player("Sender", overworld)
        val near = Fakes.player("Near", overworld, x = 10.0)
        val far = Fakes.player("Far", overworld, x = 500.0)
        val recipients = ChatService.localRecipients(sender, listOf(sender, near, far), 50)

        assertEquals(listOf(near), ChatService.onlyRecipients(listOf(near, far), recipients))
    }
}
