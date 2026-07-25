package zorahm.zochat.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class PrefixFormatter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private PrefixFormatter() {
    }

    // Parse a legacy-coded LuckPerms meta value (&c, §c, &#ff5555, §x§f§f...) into a Component.
    public static Component format(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        // Normalize § to & so one serializer handles both styles (incl. §x§f§f... hex).
        String normalized = raw.replace('§', '&');
        return LEGACY.deserialize(normalized);
    }

    // Convert a LuckPerms meta value to a MiniMessage string: translate legacy codes
    // (&c, §c, &#ff5555, §x§r§r...) to MiniMessage tags while leaving existing MiniMessage
    // tags (<#ff5555>, <gradient:...>) untouched, so admins can mix both. Tags are left
    // unclosed (like legacy), which lets an open colour in a suffix flow into following text.
    public static String toMiniMessage(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 16);
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < n) {
                char code = raw.charAt(i + 1);
                // Bukkit hex: &x&r&r&g&g&b&b (or § variant) -> <#rrggbb>
                if ((code == 'x' || code == 'X') && i + 13 < n) {
                    String hex = readRepeatedHex(raw, i);
                    if (hex != null) {
                        out.append("<#").append(hex).append('>');
                        i += 14;
                        continue;
                    }
                }
                // &#rrggbb -> <#rrggbb>
                if (code == '#' && i + 7 < n && isHex(raw, i + 2, 6)) {
                    out.append("<#").append(raw, i + 2, i + 8).append('>');
                    i += 8;
                    continue;
                }
                // single-character legacy code -> MiniMessage tag
                String tag = tagFor(Character.toLowerCase(code));
                if (tag != null) {
                    out.append(tag);
                    i += 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    // Reads 6 hex digits from a &x&r&r&g&g&b&b sequence starting at the '&'/'§' before 'x'.
    private static String readRepeatedHex(String s, int amp) {
        StringBuilder hex = new StringBuilder(6);
        for (int k = 0; k < 6; k++) {
            int markerIdx = amp + 2 + k * 2;
            int digitIdx = markerIdx + 1;
            if (digitIdx >= s.length()) {
                return null;
            }
            char marker = s.charAt(markerIdx);
            char digit = s.charAt(digitIdx);
            if ((marker != '&' && marker != '§') || !isHexDigit(digit)) {
                return null;
            }
            hex.append(digit);
        }
        return hex.toString();
    }

    private static boolean isHex(String s, int start, int count) {
        for (int k = 0; k < count; k++) {
            if (!isHexDigit(s.charAt(start + k))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static String tagFor(char code) {
        switch (code) {
            case '0': return "<black>";
            case '1': return "<dark_blue>";
            case '2': return "<dark_green>";
            case '3': return "<dark_aqua>";
            case '4': return "<dark_red>";
            case '5': return "<dark_purple>";
            case '6': return "<gold>";
            case '7': return "<gray>";
            case '8': return "<dark_gray>";
            case '9': return "<blue>";
            case 'a': return "<green>";
            case 'b': return "<aqua>";
            case 'c': return "<red>";
            case 'd': return "<light_purple>";
            case 'e': return "<yellow>";
            case 'f': return "<white>";
            case 'k': return "<obfuscated>";
            case 'l': return "<bold>";
            case 'm': return "<strikethrough>";
            case 'n': return "<underlined>";
            case 'o': return "<italic>";
            case 'r': return "<reset>";
            default: return null;
        }
    }
}
