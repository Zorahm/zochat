package zorahm.zochat.util

import net.kyori.adventure.text.minimessage.MiniMessage

object PlayerText {

    private val MM = MiniMessage.miniMessage()

    /**
     * Escapes untrusted text for splicing into a MiniMessage string so it renders exactly as typed.
     * escapeTags alone isn't enough: MiniMessage also uses '\' as its escape character, so a player's
     * trailing '\' escaped the trusted tag spliced right after it (a mention highlight showed as raw
     * `<yellow>...`), and a typed "\<red>" lost its backslash. Doubling backslashes first makes every
     * one of them literal.
     */
    @JvmStatic
    fun escape(text: String): String = MM.escapeTags(text.replace("\\", "\\\\"))
}
