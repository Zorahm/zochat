package zorahm.zochat.chat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MentionPatternTest {

    @Test
    fun everyoneMatchesItsAliasesAsWholeWords() {
        assertTrue(everyone("@everyone hi"))
        assertTrue(everyone("hey @all!"))
        assertTrue(everyone("@все"))
    }

    // Without a boundary "@Allan" was read as "@all" + "an" and pinged the whole server.
    @Test
    fun everyoneIgnoresLongerNames() {
        assertFalse(everyone("hi @Allan"))
        assertFalse(everyone("@all_star"))
        assertFalse(everyone("@everyone1"))
    }

    @Test
    fun cyrillicAliasesAreCaseInsensitive() {
        assertTrue(everyone("@ВСЕ"))
        assertTrue(here("@ЗДЕСЬ"))
    }

    @Test
    fun hereIgnoresLongerWords() {
        assertTrue(here("@here, come"))
        assertFalse(here("@heres"))
    }

    private fun everyone(s: String) = MentionHandler.EVERYONE_PATTERN.matcher(s).find()

    private fun here(s: String) = MentionHandler.HERE_PATTERN.matcher(s).find()
}
