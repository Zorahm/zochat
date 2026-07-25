package zorahm.zochat.announcer

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import zorahm.zochat.chat.PapiHook
import zorahm.zochat.util.Sounds

/**
 * Drives the timed chat announcers. Each enabled announcer gets its own repeating main-thread task,
 * so delivery, PlaceholderAPI resolution and sounds all run where Bukkit expects them.
 */
class AnnouncerService(
    private val plugin: Plugin,
    private val config: AnnouncerConfig,
    private val papi: PapiHook,
) {
    private val mm = MiniMessage.miniMessage()
    private val tasks = mutableListOf<BukkitTask>()

    fun start() {
        if (!config.isEnabled) return
        config.announcers().forEachIndexed { index, announcer ->
            if (!announcer.enabled) return@forEachIndexed
            if (announcer.intervalSeconds <= 0) {
                plugin.logger.warning("Announcer #${index + 1} skipped: interval must be > 0")
                return@forEachIndexed
            }
            if (announcer.announcementIds.isEmpty()) {
                plugin.logger.warning("Announcer #${index + 1} skipped: empty announcements list")
                return@forEachIndexed
            }
            val selector = AnnouncementSelector(announcer.announcementIds, announcer.selectionType)
            val periodTicks = announcer.intervalSeconds * 20L
            // First broadcast after one full interval, not the moment the plugin enables. The extra
            // per-announcer second staggers same-interval announcers so they never all resolve PAPI
            // and parse MiniMessage in the same tick.
            tasks += Bukkit.getScheduler().runTaskTimer(
                plugin, Runnable { broadcastNext(selector) }, periodTicks + index * 20L, periodTicks
            )
        }
    }

    fun stop() {
        tasks.forEach { it.cancel() }
        tasks.clear()
    }

    fun reload() {
        stop()
        config.reload()
        start()
    }

    private fun broadcastNext(selector: AnnouncementSelector) {
        val id = selector.next() ?: return
        val announcement = config.announcement(id)
        if (announcement == null) {
            plugin.logger.warning("Announcement '$id' is referenced by an announcer but not defined")
            return
        }
        if (announcement.lines.isEmpty()) return

        val sound = Sounds.parse(announcement.sound)

        // Announcements are admin-authored (trusted) — MiniMessage is parsed directly. But parsing
        // (regex-heavy, gradients especially) and PAPI expansions used to run per player × per line
        // in one tick, which dropped TPS on populated servers. Lines are near-always identical for
        // every recipient, so:
        //  - no '%' (or no PAPI): parse once up front, share the immutable Component with everyone
        //  - with '%': PAPI must resolve per recipient (%player_*% personalises the copy), but equal
        //    results (e.g. %server_tps%) still hit one deserialize via the memo cache
        val preParsed = arrayOfNulls<Component>(announcement.lines.size)
        for ((i, line) in announcement.lines.withIndex()) {
            if (!papi.isAvailable || '%' !in line) preParsed[i] = mm.deserialize(line)
        }
        val memo = HashMap<String, Component>()

        for (player in Bukkit.getOnlinePlayers()) {
            if (!canSee(player, announcement)) continue
            for ((i, line) in announcement.lines.withIndex()) {
                val component = preParsed[i] ?: run {
                    val resolved = papi.apply(player, line)
                    memo.getOrPut(resolved) { mm.deserialize(resolved) }
                }
                player.sendMessage(component)
            }
            sound.ifPresent { player.playSound(player.location, it, 1.0f, 1.0f) }
        }
    }

    private fun canSee(player: Player, announcement: Announcement): Boolean {
        if (announcement.permission.isNotEmpty() && !player.hasPermission(announcement.permission)) return false
        if (announcement.worlds.isNotEmpty() && player.world.name !in announcement.worlds) return false
        return true
    }
}
