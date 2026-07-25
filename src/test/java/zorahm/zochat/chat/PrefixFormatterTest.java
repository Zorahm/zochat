package zorahm.zochat.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class PrefixFormatterTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void miniMessageLeavesLegacyCodesRaw() {
        String shownByOldCode = PLAIN.serialize(MiniMessage.miniMessage().deserialize("&c[Admin] "));
        assertEquals("&c[Admin] ", shownByOldCode);
    }

    @Test
    void ampersandColorIsParsed() {
        Component result = PrefixFormatter.format("&c[Admin] ");
        assertEquals("[Admin] ", PLAIN.serialize(result));
        assertTrue(PrefixFormatter.toMiniMessage("&c[Admin] ").contains("<red>"));
    }

    @Test
    void sectionColorIsParsed() {
        Component result = PrefixFormatter.format("§c[Admin]");
        assertEquals("[Admin]", PLAIN.serialize(result));
        assertTrue(PrefixFormatter.toMiniMessage("§c[Admin]").contains("<red>"));
    }

    // Check the actual parsed color, not the MiniMessage serialization string
    // (serialization may collapse hex to named color, etc.).
    @Test
    void ampersandHexIsParsed() {
        Component result = PrefixFormatter.format("&#a1b2c3[VIP]");
        assertEquals("[VIP]", PLAIN.serialize(result));
        assertEquals(TextColor.color(0xa1b2c3), firstColor(result));
    }

    @Test
    void sectionUnusualHexFormatIsParsed() {
        Component result = PrefixFormatter.format("§x§a§1§b§2§c§3[VIP]");
        assertEquals("[VIP]", PLAIN.serialize(result));
        assertEquals(TextColor.color(0xa1b2c3), firstColor(result));
    }

    private static TextColor firstColor(Component c) {
        if (c.color() != null) {
            return c.color();
        }
        for (Component child : c.children()) {
            TextColor found = firstColor(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void emptyAndNullAreSafe() {
        assertEquals(Component.empty(), PrefixFormatter.format(""));
        assertEquals(Component.empty(), PrefixFormatter.format(null));
        assertEquals("", PrefixFormatter.toMiniMessage(""));
        assertEquals("", PrefixFormatter.toMiniMessage(null));
    }

    @Test
    void toMiniMessageStripsLegacyCodes() {
        String mm = PrefixFormatter.toMiniMessage("&c[Admin] ");
        assertFalse(mm.contains("&c"));
        assertEquals("[Admin] ", PLAIN.serialize(MiniMessage.miniMessage().deserialize(mm)));
    }

    @Test
    void toMiniMessagePreservesExistingMiniMessageTags() {
        assertEquals("<#ff5555>", PrefixFormatter.toMiniMessage("<#ff5555>"));
        assertEquals("<gradient:#55ff55:#aaffaa>x</gradient>",
                PrefixFormatter.toMiniMessage("<gradient:#55ff55:#aaffaa>x</gradient>"));
    }

    @Test
    void toMiniMessageTranslatesLegacyColors() {
        assertEquals("<red>Hi", PrefixFormatter.toMiniMessage("&cHi"));
        assertEquals("<red>Hi", PrefixFormatter.toMiniMessage("§cHi"));
        assertEquals("<#ff5555>X", PrefixFormatter.toMiniMessage("&#ff5555X"));
        assertEquals("<#ff5555>Y", PrefixFormatter.toMiniMessage("§x§f§f§5§5§5§5Y"));
    }

    @Test
    void toMiniMessageHandlesMixedLegacyAndMiniMessage() {
        assertEquals("<red><bold>Z", PrefixFormatter.toMiniMessage("&c<bold>Z"));
    }

    // Verifies the ChatService assembly: an unclosed colour inlined before a replaced
    // {message} component flows into that component (so a <#hex> suffix recolours the message).
    @Test
    void suffixColourFlowsIntoReplacedMessage() {
        Component base = MiniMessage.miniMessage().deserialize("<#a1b2c3>{message}");
        Component result = base.replaceText(b -> b.matchLiteral("{message}")
                .replacement(Component.text("hello")));
        assertEquals(TextColor.color(0xa1b2c3), effectiveColor(result, "hello"));
    }

    private static TextColor effectiveColor(Component c, String text) {
        return effectiveColor(c, text, null);
    }

    private static TextColor effectiveColor(Component c, String text, TextColor inherited) {
        TextColor current = c.color() != null ? c.color() : inherited;
        if (c instanceof TextComponent tc && tc.content().contains(text)) {
            return current;
        }
        for (Component child : c.children()) {
            TextColor found = effectiveColor(child, text, current);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
