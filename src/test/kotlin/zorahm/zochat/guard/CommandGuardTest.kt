package zorahm.zochat.guard

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandGuardTest {

    private val guard = CommandGuard(listOf("op", "deop", "seed", "stop"), blockNamespaced = true)

    @Test
    fun blocksListedCommandWithLeadingSlashAndArgs() {
        assertTrue(guard.isBlocked("/op Zorahm"))
        assertTrue(guard.isBlocked("/seed"))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertTrue(guard.isBlocked("/OP Zorahm"))
        assertTrue(guard.isBlocked("/Seed"))
    }

    @Test
    fun blocksNamespacedBypassWhenEnabled() {
        assertTrue(guard.isBlocked("/minecraft:op Zorahm"))
        assertTrue(guard.isBlocked("/bukkit:seed"))
    }

    @Test
    fun allowsNamespacedFormWhenNamespaceBlockingDisabled() {
        val strict = CommandGuard(listOf("op"), blockNamespaced = false)
        assertTrue(strict.isBlocked("/op"))
        assertFalse(strict.isBlocked("/minecraft:op"))
    }

    @Test
    fun allowsUnlistedCommands() {
        assertFalse(guard.isBlocked("/spawn"))
        assertFalse(guard.isBlocked("/msg Zorahm hi"))
    }

    @Test
    fun doesNotMatchOnPrefix() {
        // "/seedvault" must not be caught by the "seed" rule — matching is on the whole name.
        assertFalse(guard.isBlocked("/seedvault"))
    }

    @Test
    fun matchesBareCommandNameFromTabList() {
        // PlayerCommandSendEvent hands us bare names without a leading slash.
        assertTrue(guard.isBlocked("op"))
        assertTrue(guard.isBlocked("minecraft:deop"))
    }

    @Test
    fun ignoresBlankInput() {
        assertFalse(guard.isBlocked(""))
        assertFalse(guard.isBlocked("   "))
        assertFalse(guard.isBlocked("/"))
    }

    @Test
    fun configEntriesAreNormalizedToo() {
        // Entries authored with a slash or a namespace prefix still work.
        val g = CommandGuard(listOf("/op", "minecraft:seed"), blockNamespaced = true)
        assertTrue(g.isBlocked("/op"))
        assertTrue(g.isBlocked("/seed"))
    }

    @Test
    fun emptyReflectsBlockedSet() {
        assertTrue(CommandGuard(emptyList(), blockNamespaced = true).isEmpty)
        assertFalse(guard.isEmpty)
    }
}
