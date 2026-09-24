package zorahm.zochat.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MentionPatternTest {

    private fun everyone(text: String) = MentionHandler.EVERYONE_PATTERN.matcher(text).find()
    private fun here(text: String) = MentionHandler.HERE_PATTERN.matcher(text).find()

    @Test
    fun everyoneMatchesStandaloneAliases() {
        assertTrue(everyone("@all"))
        assertTrue(everyone("hey @ALL!"))
        assertTrue(everyone("@everyone, look"))
        assertTrue(everyone("@все"))
        assertTrue(everyone("@Все сюда"))
    }

    @Test
    fun everyoneIgnoresLongerNames() {
        assertFalse(everyone("@Allen hi"))
        assertFalse(everyone("mail me at bob@allmail.com"))
        assertFalse(everyone("@all_stars"))
        assertFalse(everyone("@everyones"))
        assertFalse(everyone("@всем"))
    }

    @Test
    fun hereRespectsWordBoundary() {
        assertTrue(here("@here"))
        assertTrue(here("@Здесь!"))
        assertFalse(here("@hereford"))
        assertFalse(here("@heres"))
    }

    @Test
    fun highlightLeavesLongerNamesAlone() {
        assertEquals(
            "<red>@everyone</red> and @Allen",
            MentionHandler.highlight(MentionHandler.EVERYONE_PATTERN, "@all and @Allen", "<red>@everyone</red>")
        )
    }

    @Test
    fun highlightTreatsDollarInFormatLiterally() {
        assertEquals(
            "hi <gold>\$1 @here</gold>",
            MentionHandler.highlight(MentionHandler.HERE_PATTERN, "hi @here", "<gold>\$1 @here</gold>")
        )
    }
}
