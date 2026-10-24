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

    // Only the first token used to be checked, so wrapping a blocked command in /execute ran it.
    @Test
    fun blocksCommandWrappedInExecuteRun() {
        assertTrue(guard.isBlocked("/execute as @s run op Zorahm"))
        assertTrue(guard.isBlocked("/minecraft:execute run seed"))
        assertTrue(guard.isBlocked("/execute at @p run execute run deop Zorahm"))
    }

    @Test
    fun allowsExecuteOfUnlistedCommand() {
        assertFalse(guard.isBlocked("/execute as @a run say hi"))
        assertFalse(guard.isBlocked("execute"))
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
