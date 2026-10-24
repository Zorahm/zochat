package zorahm.zochat.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zorahm.zochat.chat.BannedWordsFilter.Action
import zorahm.zochat.chat.BannedWordsFilter.Mode

class BannedWordsFilterTest {

    @Test
    fun normalizeStripsSpacesRepeatsAndLeet() {
        assertEquals("hello", BannedWordsFilter.normalize("h e l l o"))
        assertEquals("hello", BannedWordsFilter.normalize("heeeello"))
        assertEquals("hello", BannedWordsFilter.normalize("h3ll0"))
    }

    @Test
    fun normalizeWithoutLeetKeepsDigits() {
        assertEquals("h3ll0", BannedWordsFilter.normalize("h3ll0", false))
    }

    @Test
    fun blockCatchesLeetBypass() {
        val r = BannedWordsFilter.apply("say h3llo there", listOf("hello"), Mode.CONTAINS, Action.BLOCK, true)
        assertTrue(r.isBlocked)
        assertEquals("hello", r.matchedWord)
    }

    @Test
    fun blockCatchesSpacedBypass() {
        val r = BannedWordsFilter.apply("b a d word", listOf("bad"), Mode.CONTAINS, Action.BLOCK, true)
        assertTrue(r.isBlocked)
    }

    @Test
    fun leetToggleOffMissesLeet() {
        val r = BannedWordsFilter.apply("h3llo", listOf("hello"), Mode.CONTAINS, Action.BLOCK, false)
        assertFalse(r.isBlocked)
    }

    @Test
    fun smartBoundarySkipsSubstring() {
        val r = BannedWordsFilter.apply("classic", listOf("ass"), Mode.SMART, Action.BLOCK, true)
        assertFalse(r.isBlocked)
    }

    @Test
    fun smartMatchesStandaloneWord() {
        val r = BannedWordsFilter.apply("you ass", listOf("ass"), Mode.SMART, Action.BLOCK, true)
        assertTrue(r.isBlocked)
    }

    @Test
    fun containsMatchesSubstring() {
        val r = BannedWordsFilter.apply("classic", listOf("ass"), Mode.CONTAINS, Action.BLOCK, true)
        assertTrue(r.isBlocked)
    }

    @Test
    fun replaceCensorsAndDoesNotBlock() {
        val r = BannedWordsFilter.apply("you are bad", listOf("bad"), Mode.CONTAINS, Action.REPLACE, true)
        assertFalse(r.isBlocked)
        assertEquals("you are ***", r.processedMessage)
    }

    @Test
    fun replaceCensorsBypassInOriginalText() {
        // "b a d" normalizes to "bad" and must be censored in the original as a whole.
        val r = BannedWordsFilter.apply("you b a d", listOf("bad"), Mode.CONTAINS, Action.REPLACE, true)
        assertFalse(r.isBlocked)
        assertFalse(r.processedMessage.contains("b a d"))
    }

    @Test
    fun regexIsSupported() {
        val r = BannedWordsFilter.apply(
            "card 1234-5678-9012-3456",
            listOf("regex:\\b\\d{4}-\\d{4}-\\d{4}-\\d{4}\\b"),
            Mode.SMART, Action.BLOCK, true
        )
        assertTrue(r.isBlocked)
    }

    // Repeats used to collapse 3+ to one char while a double stayed double, so "asss" became "as"
    // and slipped past "ass". A run of 3+ now stands for any count of that letter.
    @Test
    fun tripledDoubleLetterIsStillCaught() {
        assertTrue(BannedWordsFilter.apply("you asss", listOf("ass"), Mode.SMART, Action.BLOCK, true).isBlocked)
        assertTrue(BannedWordsFilter.apply("booob", listOf("boob"), Mode.SMART, Action.BLOCK, true).isBlocked)
    }

    @Test
    fun tripledSingleLetterIsStillCaught() {
        assertTrue(BannedWordsFilter.apply("baaad", listOf("bad"), Mode.SMART, Action.BLOCK, true).isBlocked)
    }

    // A double is a real spelling difference ("good" vs "god"), not an emphasis — keep them apart.
    @Test
    fun doubleLetterStaysDistinctFromSingle() {
        assertFalse(BannedWordsFilter.apply("good", listOf("god"), Mode.SMART, Action.BLOCK, true).isBlocked)
    }

    @Test
    fun fewerRepeatsThanTheWordDoNotMatch() {
        assertFalse(BannedWordsFilter.apply("as", listOf("ass"), Mode.SMART, Action.BLOCK, true).isBlocked)
    }

    @Test
    fun replaceCensorsTheWholeRepeatRun() {
        val r = BannedWordsFilter.apply("you asss", listOf("ass"), Mode.SMART, Action.REPLACE, true)
        assertEquals("you ****", r.processedMessage)
    }

    @Test
    fun cleanMessagePasses() {
        val r = BannedWordsFilter.apply("hello friend", listOf("bad"), Mode.SMART, Action.BLOCK, true)
        assertFalse(r.isBlocked)
        assertEquals("hello friend", r.processedMessage)
    }
}
