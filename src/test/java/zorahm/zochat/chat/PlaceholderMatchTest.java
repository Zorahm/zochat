package zorahm.zochat.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PlaceholderMatchTest {

    private static List<String> matches(Pattern pattern, String input) {
        List<String> found = new ArrayList<>();
        if (pattern == null) {
            return found;
        }
        Matcher m = pattern.matcher(input);
        while (m.find()) {
            found.add(m.group(1).toLowerCase());
        }
        return found;
    }

    @Test
    void longerTokenWinsOverShorterAlias() {
        Pattern p = PlaceholderService.buildPattern(new ArrayList<>(List.of("world", "w")));
        // "^world" must match the whole "world", never the "w" alias inside it.
        assertEquals(List.of("world"), matches(p, "at ^world now"));
    }

    @Test
    void shortAliasDoesNotMatchInsideLongerWord() {
        Pattern p = PlaceholderService.buildPattern(new ArrayList<>(List.of("w")));
        // "^w" is a placeholder, but "^wave" is just text — the boundary lookahead keeps them apart.
        assertEquals(List.of(), matches(p, "^wave"));
        assertEquals(List.of("w"), matches(p, "in ^w yes"));
    }

    @Test
    void matchesCyrillicAliasCaseInsensitively() {
        Pattern p = PlaceholderService.buildPattern(new ArrayList<>(List.of("где")));
        assertEquals(List.of("где"), matches(p, "я ^Где стою"));
    }

    @Test
    void emptyTokensGiveNullPattern() {
        assertNull(PlaceholderService.buildPattern(new ArrayList<>()));
    }
}
