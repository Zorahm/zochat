package zorahm.zochat.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object PrefixFormatter {

    private val LEGACY: LegacyComponentSerializer = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    // Parse a legacy-coded LuckPerms meta value (&c, §c, &#ff5555, §x§f§f...) into a Component.
    @JvmStatic
    fun format(raw: String?): Component {
        if (raw.isNullOrEmpty()) {
            return Component.empty()
        }
        // Normalize § to & so one serializer handles both styles (incl. §x§f§f... hex).
        val normalized = raw.replace('§', '&')
        return LEGACY.deserialize(normalized)
    }

    // Convert a LuckPerms meta value to a MiniMessage string: translate legacy codes
    // (&c, §c, &#ff5555, §x§r§r...) to MiniMessage tags while leaving existing MiniMessage
    // tags (<#ff5555>, <gradient:...>) untouched, so admins can mix both. Tags are left
    // unclosed (like legacy), which lets an open colour in a suffix flow into following text.
    @JvmStatic
    fun toMiniMessage(raw: String?): String {
        if (raw.isNullOrEmpty()) {
            return ""
        }
        val out = StringBuilder(raw.length + 16)
        // In legacy text a colour code ends bold/italic/etc.; MiniMessage would nest the colour
        // inside them instead, so "&c&l[Admin]&7 " left the name and message bold. Track what the
        // legacy codes opened and negate exactly that — a <bold> from the surrounding chat format
        // is the admin's choice and must survive.
        val openDecorations = LinkedHashSet<String>()
        var i = 0
        val n = raw.length
        while (i < n) {
            val c = raw[i]
            if ((c == '&' || c == '§') && i + 1 < n) {
                val code = raw[i + 1]
                // Bukkit hex: &x&r&r&g&g&b&b (or § variant) -> <#rrggbb>
                if ((code == 'x' || code == 'X') && i + 13 < n) {
                    val hex = readRepeatedHex(raw, i)
                    if (hex != null) {
                        endDecorations(out, openDecorations)
                        out.append("<#").append(hex).append('>')
                        i += 14
                        continue
                    }
                }
                // &#rrggbb -> <#rrggbb>
                if (code == '#' && i + 7 < n && isHex(raw, i + 2, 6)) {
                    endDecorations(out, openDecorations)
                    out.append("<#").append(raw, i + 2, i + 8).append('>')
                    i += 8
                    continue
                }
                val lower = code.lowercaseChar()
                val color = colorTag(lower)
                if (color != null) {
                    endDecorations(out, openDecorations)
                    out.append(color)
                    i += 2
                    continue
                }
                val decoration = decorationName(lower)
                if (decoration != null) {
                    openDecorations += decoration
                    out.append('<').append(decoration).append('>')
                    i += 2
                    continue
                }
                if (lower == 'r') {
                    openDecorations.clear()
                    out.append("<reset>")
                    i += 2
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun endDecorations(out: StringBuilder, open: MutableSet<String>) {
        for (decoration in open) {
            out.append("<!").append(decoration).append('>')
        }
        open.clear()
    }

    // Reads 6 hex digits from a &x&r&r&g&g&b&b sequence starting at the '&'/'§' before 'x'.
    private fun readRepeatedHex(s: String, amp: Int): String? {
        val hex = StringBuilder(6)
        for (k in 0 until 6) {
            val markerIdx = amp + 2 + k * 2
            val digitIdx = markerIdx + 1
            if (digitIdx >= s.length) {
                return null
            }
            val marker = s[markerIdx]
            val digit = s[digitIdx]
            if ((marker != '&' && marker != '§') || !isHexDigit(digit)) {
                return null
            }
            hex.append(digit)
        }
        return hex.toString()
    }

    private fun isHex(s: String, start: Int, count: Int): Boolean {
        for (k in 0 until count) {
            if (!isHexDigit(s[start + k])) {
                return false
            }
        }
        return true
    }

    private fun isHexDigit(c: Char): Boolean =
        (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')

    private fun colorTag(code: Char): String? = when (code) {
        '0' -> "<black>"
        '1' -> "<dark_blue>"
        '2' -> "<dark_green>"
        '3' -> "<dark_aqua>"
        '4' -> "<dark_red>"
        '5' -> "<dark_purple>"
        '6' -> "<gold>"
        '7' -> "<gray>"
        '8' -> "<dark_gray>"
        '9' -> "<blue>"
        'a' -> "<green>"
        'b' -> "<aqua>"
        'c' -> "<red>"
        'd' -> "<light_purple>"
        'e' -> "<yellow>"
        'f' -> "<white>"
        else -> null
    }

    private fun decorationName(code: Char): String? = when (code) {
        'k' -> "obfuscated"
        'l' -> "bold"
        'm' -> "strikethrough"
        'n' -> "underlined"
        'o' -> "italic"
        else -> null
    }
}
