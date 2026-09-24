package zorahm.zochat.advancement

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class AdvancementListenerTest {

    private val hover = HoverEvent.showText(Component.translatable("advancements.story.mine_stone.description"))

    // What Paper's Advancement.displayName() looks like: "[<translated title>]" coloured by frame, description on hover.
    private val advancement = Component.text("[")
        .append(Component.translatable("advancements.story.mine_stone.title"))
        .append(Component.text("]"))
        .color(NamedTextColor.GREEN)
        .hoverEvent(hover)
    private val player = Component.text("Steve")

    private fun translatables(c: Component): List<TranslatableComponent> =
        (if (c is TranslatableComponent) listOf(c) else emptyList()) + c.children().flatMap { translatables(it) }

    private fun containsHover(c: Component): Boolean =
        c.hoverEvent() == hover || c.children().any { containsHover(it) } ||
            (c is TranslatableComponent && c.arguments().any { containsHover(it.asComponent()) })

    @Test
    fun langFormatKeepsPhraseAndAdvancementTranslatable() {
        val rendered = AdvancementListener.render(
            "<gray>✦ <lang:chat.type.advancement.task:'<white>{player}</white>':'{advancement}'>", player, advancement
        )

        val phrase = translatables(rendered).single { it.key() == "chat.type.advancement.task" }
        assertEquals(2, phrase.arguments().size)
        assertEquals(player.content(), (phrase.arguments()[0].asComponent() as TextComponent).content())
        val adv = phrase.arguments()[1].asComponent()
        assertTrue(containsHover(adv), "the description hover must survive: $adv")
        assertTrue(translatables(adv).any { it.key() == "advancements.story.mine_stone.title" }, "title must stay translatable")
    }

    @Test
    fun hoverSurvivesAGradientAroundTheTokens() {
        val rendered = AdvancementListener.render("<gradient:#ff0000:#0000ff>{player} got {advancement}", player, advancement)
        assertTrue(containsHover(rendered), "an enclosing gradient must not drop the hover: $rendered")
        assertTrue(translatables(rendered).any { it.key() == "advancements.story.mine_stone.title" })
    }

    @Test
    fun shippedDefaultFormatsAreValidAndLocalised() {
        val stream = javaClass.classLoader.getResourceAsStream("advancements.yml")
        assertNotNull(stream, "advancements.yml must be bundled")
        val cfg = YamlConfiguration.loadConfiguration(InputStreamReader(stream!!, Charsets.UTF_8))

        for (frame in listOf("task", "goal", "challenge")) {
            val format = cfg.getString("formats.$frame")
            assertNotNull(format, frame)
            val rendered = AdvancementListener.render(format!!, player, advancement)
            val phrase = translatables(rendered).single { it.key() == "chat.type.advancement.$frame" }
            assertTrue(containsHover(phrase), "$frame: advancement hover lost")
        }
    }
}
