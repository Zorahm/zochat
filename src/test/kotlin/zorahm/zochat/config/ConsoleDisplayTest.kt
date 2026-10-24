package zorahm.zochat.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConsoleDisplayTest {

    @Test
    fun prefixAndNameAreJoined() {
        assertEquals("<badge:console> Server", ChatConfig.consoleDisplay("<badge:console> ", "Server"))
    }

    // The CONSOLE badge already says who it is, so an admin can blank the name; the prefix's trailing
    // space must not be left dangling before the colon in "ЛС от <badge>:".
    @Test
    fun blankNameDropsTheTrailingSpaceOfThePrefix() {
        assertEquals("<badge:console>", ChatConfig.consoleDisplay("<badge:console> ", ""))
    }

    @Test
    fun nameWithoutPrefix() {
        assertEquals("Server", ChatConfig.consoleDisplay("", "Server"))
    }
}
