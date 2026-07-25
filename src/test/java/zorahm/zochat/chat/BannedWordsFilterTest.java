package zorahm.zochat.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import zorahm.zochat.chat.BannedWordsFilter.Action;
import zorahm.zochat.chat.BannedWordsFilter.FilterResult;
import zorahm.zochat.chat.BannedWordsFilter.Mode;

class BannedWordsFilterTest {

    @Test
    void normalizeStripsSpacesRepeatsAndLeet() {
        assertEquals("hello", BannedWordsFilter.normalize("h e l l o"));
        assertEquals("hello", BannedWordsFilter.normalize("heeeello"));
        assertEquals("hello", BannedWordsFilter.normalize("h3ll0"));
    }

    @Test
    void normalizeWithoutLeetKeepsDigits() {
        assertEquals("h3ll0", BannedWordsFilter.normalize("h3ll0", false));
    }

    @Test
    void blockCatchesLeetBypass() {
        FilterResult r = BannedWordsFilter.apply("say h3llo there", List.of("hello"), Mode.CONTAINS, Action.BLOCK, true);
        assertTrue(r.isBlocked());
        assertEquals("hello", r.getMatchedWord());
    }

    @Test
    void blockCatchesSpacedBypass() {
        FilterResult r = BannedWordsFilter.apply("b a d word", List.of("bad"), Mode.CONTAINS, Action.BLOCK, true);
        assertTrue(r.isBlocked());
    }

    @Test
    void leetToggleOffMissesLeet() {
        FilterResult r = BannedWordsFilter.apply("h3llo", List.of("hello"), Mode.CONTAINS, Action.BLOCK, false);
        assertFalse(r.isBlocked());
    }

    @Test
    void smartBoundarySkipsSubstring() {
        FilterResult r = BannedWordsFilter.apply("classic", List.of("ass"), Mode.SMART, Action.BLOCK, true);
        assertFalse(r.isBlocked());
    }

    @Test
    void smartMatchesStandaloneWord() {
        FilterResult r = BannedWordsFilter.apply("you ass", List.of("ass"), Mode.SMART, Action.BLOCK, true);
        assertTrue(r.isBlocked());
    }

    @Test
    void containsMatchesSubstring() {
        FilterResult r = BannedWordsFilter.apply("classic", List.of("ass"), Mode.CONTAINS, Action.BLOCK, true);
        assertTrue(r.isBlocked());
    }

    @Test
    void replaceCensorsAndDoesNotBlock() {
        FilterResult r = BannedWordsFilter.apply("you are bad", List.of("bad"), Mode.CONTAINS, Action.REPLACE, true);
        assertFalse(r.isBlocked());
        assertEquals("you are ***", r.getProcessedMessage());
    }

    @Test
    void replaceCensorsBypassInOriginalText() {
        // "b a d" normalizes to "bad" and must be censored in the original as a whole.
        FilterResult r = BannedWordsFilter.apply("you b a d", List.of("bad"), Mode.CONTAINS, Action.REPLACE, true);
        assertFalse(r.isBlocked());
        assertFalse(r.getProcessedMessage().contains("b a d"));
    }

    @Test
    void regexIsSupported() {
        FilterResult r = BannedWordsFilter.apply(
                "card 1234-5678-9012-3456",
                List.of("regex:\\b\\d{4}-\\d{4}-\\d{4}-\\d{4}\\b"),
                Mode.SMART, Action.BLOCK, true);
        assertTrue(r.isBlocked());
    }

    @Test
    void cleanMessagePasses() {
        FilterResult r = BannedWordsFilter.apply("hello friend", List.of("bad"), Mode.SMART, Action.BLOCK, true);
        assertFalse(r.isBlocked());
        assertEquals("hello friend", r.getProcessedMessage());
    }
}
