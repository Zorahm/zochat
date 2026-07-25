package zorahm.zochat.announcer

import kotlin.random.Random

/**
 * Picks the next announcement ID for a single announcer and owns its SEQUENTIAL cursor. Pure logic
 * (no Bukkit), so the rotation behaviour is unit-testable; [random] is injectable for deterministic
 * RANDOM tests.
 */
class AnnouncementSelector(
    private val ids: List<String>,
    private val type: SelectionType,
    private val random: Random = Random.Default,
) {
    private var cursor = 0

    fun next(): String? {
        if (ids.isEmpty()) return null
        return when (type) {
            SelectionType.RANDOM -> ids[random.nextInt(ids.size)]
            SelectionType.SEQUENTIAL -> ids[cursor].also { cursor = (cursor + 1) % ids.size }
        }
    }
}
