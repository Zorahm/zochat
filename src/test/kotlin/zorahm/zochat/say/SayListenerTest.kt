package zorahm.zochat.say

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import zorahm.zochat.Fakes

class SayListenerTest {

    private val op = Fakes.player("Admin", permissions = setOf(SayListener.SAY_PERMISSION))
    private val regular = Fakes.player("Steve")

    @Test
    fun playerWithoutSayPermissionIsLeftToVanilla() {
        assertNull(SayListener.playerSayText(regular, "/say hello everyone"))
        assertNull(SayListener.playerSayText(regular, "/minecraft:say hello everyone"))
    }

    @Test
    fun playerWithSayPermissionIsFormatted() {
        assertEquals("hello everyone", SayListener.playerSayText(op, "/say hello everyone"))
        assertEquals("hello", SayListener.playerSayText(op, "/minecraft:say hello"))
    }

    @Test
    fun otherCommandsAndEmptySayAreIgnored() {
        assertNull(SayListener.playerSayText(op, "/sayhello there"))
        assertNull(SayListener.playerSayText(op, "/msg Steve hi"))
        assertNull(SayListener.playerSayText(op, "/say   "))
    }
}
