package zorahm.zochat.chat

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * Per-channel anti-spam cooldowns. The previous design stored a single timestamp per UUID, so a
 * global message blocked a following local one (and vice-versa, and private messages too) — channels
 * are independent now. ConcurrentHashMap because cooldowns are touched from the main thread and from
 * PlaceholderAPI requests, which may run off the main thread.
 */
class CooldownService {

    enum class Channel { LOCAL, GLOBAL, PRIVATE }

    private val times = ConcurrentHashMap<UUID, ConcurrentHashMap<Channel, Long>>()

    /**
     * Returns true if the player is still on cooldown for this channel (the caller should block the
     * message). When not on cooldown, records "now" as the last send so the next call is rate-limited.
     * cooldownSeconds comes from config in seconds but is compared in milliseconds.
     */
    fun isOnCooldown(player: UUID, channel: Channel, cooldownSeconds: Int): Boolean {
        val now = System.currentTimeMillis()
        val perChannel = times.computeIfAbsent(player) { ConcurrentHashMap() }
        val last = perChannel[channel] ?: 0L
        if (now - last < cooldownSeconds * 1000L) return true
        perChannel[channel] = now
        return false
    }

    /** Seconds left on the cooldown ("0" when ready), for the %zochat_cooldown_*% placeholders. */
    fun remainingSeconds(player: UUID?, channel: Channel, cooldownSeconds: Int): Int {
        if (player == null) return 0
        val last = times[player]?.get(channel) ?: return 0
        val remainingMs = cooldownSeconds * 1000L - (System.currentTimeMillis() - last)
        return if (remainingMs <= 0) 0 else ceil(remainingMs / 1000.0).toInt()
    }

    fun clear(player: UUID) {
        times.remove(player)
    }

    fun reset() {
        times.clear()
    }
}
