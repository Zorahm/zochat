package zorahm.zochat.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import zorahm.zochat.chat.ChatListener.Forced
import zorahm.zochat.chat.ChatListener.Route

class ChatRouteTest {

    @Test
    fun bangSendsTheRestToGlobal() {
        assertEquals(Route.Send(ChatChannel.GLOBAL, "hi"), ChatListener.route("! hi", true, true))
    }

    @Test
    fun plainTextGoesLocalWhenLocalIsOn() {
        assertEquals(Route.Send(ChatChannel.LOCAL, "hi"), ChatListener.route("hi", true, true))
    }

    @Test
    fun plainTextGoesGlobalWhenLocalIsOff() {
        assertEquals(Route.Send(ChatChannel.GLOBAL, "hi"), ChatListener.route("hi", false, true))
    }

    // A lone "!" used to broadcast an empty global line and burn the cooldown.
    @Test
    fun bareBangIsSwallowed() {
        assertEquals(Route.Empty, ChatListener.route("!", true, true))
        assertEquals(Route.Empty, ChatListener.route("!   ", true, true))
    }

    @Test
    fun bangWhileGlobalIsOffIsRefused() {
        assertEquals(Route.GlobalDisabled, ChatListener.route("!hi", true, false))
    }

    @Test
    fun bothChannelsOffLeavesChatToVanilla() {
        assertEquals(Route.Vanilla, ChatListener.route("hi", false, false))
        assertEquals(Route.Vanilla, ChatListener.route("!hi", false, false))
    }

    // /g, /l and /bubble re-send their text as the player's own chat so mute plugins see it; the
    // command's channel wins over the "!" convention.
    @Test
    fun forcedLocalKeepsBangLiteral() {
        assertEquals(Route.Send(ChatChannel.LOCAL, "!hi"), ChatListener.route("!hi", true, true, Forced.LOCAL))
    }

    @Test
    fun forcedTextIsTrimmed() {
        // A leading space keeps Player.chat() from running "/spawn" as a command.
        assertEquals(Route.Send(ChatChannel.GLOBAL, "/spawn"), ChatListener.route(" /spawn", true, true, Forced.GLOBAL))
    }

    @Test
    fun forcedChannelThatIsOffIsRefused() {
        assertEquals(Route.GlobalDisabled, ChatListener.route("hi", true, false, Forced.GLOBAL))
        assertEquals(Route.LocalDisabled, ChatListener.route("hi", false, true, Forced.LOCAL))
        assertEquals(Route.LocalDisabled, ChatListener.route("hi", false, false, Forced.LOCAL))
    }

    @Test
    fun forcedBubbleShowsOnlyTheBubble() {
        assertEquals(Route.Bubble("hi"), ChatListener.route("hi", true, true, Forced.BUBBLE))
        assertEquals(Route.Bubble("hi"), ChatListener.route("hi", false, false, Forced.BUBBLE))
    }
}
