package zorahm.zochat.prefixgen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BadgeGlyphsTest {

    private fun rank(id: String) = ranks.first { it.id == id }

    // zoChat and every deployed pack refer to badges by character — these must never move.
    @Test
    fun codepointsAreFixedPerRankSlot() {
        assertEquals(0xE000, rank("owner").codepoint(Variant.EN))
        assertEquals(0xE003, rank("owner").codepoint(Variant.RU_ICON))
        assertEquals(0xE02C, rank("console").codepoint(Variant.EN))
        assertEquals(0xE02F, rank("console").codepoint(Variant.RU_ICON))
    }

    @Test
    fun everyBadgeHasItsOwnCodepoint() {
        val all = ranks.flatMap { r -> Variant.entries.map { r.codepoint(it) } }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun consoleBadgeRendersInBothLanguages() {
        assertEquals("CONSOLE", rank("console").en)
        assertEquals("КОНСОЛЬ", rank("console").ru)
        for (style in styles) for (variant in Variant.entries) {
            assertEquals(BADGE_HEIGHT, style.render(rank("console").badge(variant)).height)
        }
    }

    @Test
    fun fontMapsEveryBadgeToItsTexture() {
        val json = ResourcePack.fontJson(ranks)
        assertTrue(json.contains("\"file\": \"zochat:font/badges/console-ru-icon.png\""), json)
        // JSON escape, not the raw character: the file stays readable and encoding-proof.
        assertTrue(json.contains("\"chars\": [\"\\ue02f\"]"), json)
        assertEquals(ranks.size * Variant.entries.size, Regex(""""type": "bitmap"""").findAll(json).count())
    }

    @Test
    fun packTargetsMinecraft1_21_10() {
        assertTrue(ResourcePack.mcmeta().contains(""""min_format": 69"""))
    }

    // The plugin jar ships the committed badges.yml while a release builds its packs from this
    // code; if someone changes ranks without rerunning the generator, the two would disagree.
    @Test
    fun committedTableMatchesTheGenerator() {
        val committed = java.io.File(System.getProperty("zochat.badgeTable")).readText().replace("\r\n", "\n")
        assertEquals(BadgeTable.yaml(ranks), committed, "badges.yml is stale - run ./gradlew :prefixgen:run and commit it")
    }

    @Test
    fun everyStyleGetsItsOwnPack() {
        assertEquals(listOf("zochat-badges-plate", "zochat-badges-neon", "zochat-badges-craft", "zochat-badges-ribbon"), styles.map { ResourcePack.name(it) })
    }

    @Test
    fun tableListsGlyphsAndLabelsForZoChat() {
        val yaml = BadgeTable.yaml(ranks)
        assertTrue(yaml.contains("font: \"zochat:badges\""), yaml)
        assertTrue(yaml.contains("  console:\n    label-en: \"CONSOLE\"\n    label-ru: \"КОНСОЛЬ\"\n    en: \"E02C\""), yaml)
    }
}
