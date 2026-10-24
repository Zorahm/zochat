package zorahm.zochat.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

class PlaceholderMatchTest {

    @Test
    fun longerTokenWinsOverShorterAlias() {
        val p = PlaceholderService.buildPattern(listOf("world", "w"))
        // "^world" must match the whole "world", never the "w" alias inside it.
        assertEquals(listOf("world"), matches(p, "at ^world now"))
    }

    @Test
    fun shortAliasDoesNotMatchInsideLongerWord() {
        val p = PlaceholderService.buildPattern(listOf("w"))
        // "^w" is a placeholder, but "^wave" is just text — the boundary lookahead keeps them apart.
        assertEquals(emptyList<String>(), matches(p, "^wave"))
        assertEquals(listOf("w"), matches(p, "in ^w yes"))
    }

    @Test
    fun matchesCyrillicAliasCaseInsensitively() {
        val p = PlaceholderService.buildPattern(listOf("где"))
        assertEquals(listOf("где"), matches(p, "я ^Где стою"))
    }

    @Test
    fun emptyTokensGiveNullPattern() {
        assertNull(PlaceholderService.buildPattern(emptyList()))
    }

    private fun matches(pattern: Pattern?, input: String): List<String> {
        val found = ArrayList<String>()
        if (pattern == null) {
            return found
        }
        val m = pattern.matcher(input)
        while (m.find()) {
            found.add(m.group(1).lowercase())
        }
        return found
    }
}
