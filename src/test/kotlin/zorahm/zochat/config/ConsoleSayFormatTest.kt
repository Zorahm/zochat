package zorahm.zochat.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConsoleSayFormatTest {

    private val sayFormat = "{prefix}<gradient:#ffaa33:#ffd700>[{player}]</gradient> <white>{message}</white>"

    // The console shows a badge, so it can drop the "[Server]" name that players' /say needs.
    @Test
    fun consoleUsesItsOwnFormatWhenSet() {
        val config = YamlConfiguration()
        config.set("say.console-format", "{prefix}<white>{message}</white>")

        assertEquals("{prefix}<white>{message}</white>", ChatConfig.consoleSayFormat(config, sayFormat))
    }

    // The shipped config leaves the key blank so an upgrade keeps the old look.
    @Test
    fun blankConsoleFormatFallsBackToTheSayFormat() {
        val config = YamlConfiguration()
        config.set("say.console-format", "  ")

        assertEquals(sayFormat, ChatConfig.consoleSayFormat(config, sayFormat))
    }

    @Test
    fun missingConsoleFormatFallsBackToTheSayFormat() {
        assertEquals(sayFormat, ChatConfig.consoleSayFormat(YamlConfiguration(), sayFormat))
    }
}
