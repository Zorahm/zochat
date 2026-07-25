package zorahm.zochat.bubble

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import zorahm.zochat.chat.PapiHook
import java.util.UUID

/**
 * Floating chat bubbles (TextDisplay above the head). A single repeating main-thread task keeps each
 * active bubble glued to its player and removes it when it expires or the player leaves. Bubbles are
 * non-mounted and repositioned per tick, which sidesteps version-specific passenger-offset quirks.
 */
class BubbleService(
    private val plugin: Plugin,
    private val config: BubbleConfig,
    private val papi: PapiHook,
) {
    private val mm = MiniMessage.miniMessage()
    private val active = HashMap<UUID, Bubble>()
    private var task: BukkitTask? = null

    private class Bubble(val display: TextDisplay, val expiresAtMillis: Long)

    fun start() {
        if (task != null) return
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 1L, 1L)
    }

    fun stop() {
        task?.cancel()
        task = null
        clearAll()
    }

    fun reload() {
        // Config (scale/format/duration) may have changed — drop live bubbles so none keep stale state.
        config.reload()
        clearAll()
    }

    fun onChat(player: Player, message: String) {
        if (config.isEnabled && config.trigger.onChat()) show(player, message)
    }

    fun onCommand(player: Player, message: String) {
        if (config.isEnabled && config.trigger.onCommand()) show(player, message)
    }

    private fun show(player: Player, message: String) {
        if (!passesRequirements(player, message)) return
        clear(player.uniqueId) // no queue/swapper: a new message replaces the current bubble

        val display = player.world.spawn(bubbleLocation(player), TextDisplay::class.java) { d ->
            d.isPersistent = false
            d.text(render(player, message))
            d.billboard = config.billboard
            d.isSeeThrough = config.seeThrough
            d.isShadowed = config.textShadow
            d.lineWidth = config.lineWidth
            d.backgroundColor = backgroundColor()
            // Without this (default 0) the client SNAPS to each per-tick teleport from tick(), so
            // the bubble visibly stutters while the player walks; 2 ticks of client-side
            // interpolation makes it glide (at the cost of trailing ~100 ms behind the head).
            d.teleportDuration = 2
            d.transformation = Transformation(
                Vector3f(), AxisAngle4f(), Vector3f(config.scale, config.scale, config.scale), AxisAngle4f()
            )
        }
        // see-own-bubble=false: hide it from the sender only; everyone else still sees it.
        if (!config.seeOwnBubble) player.hideEntity(plugin, display)

        val symbols = message.length
        val seconds = maxOf(config.minimumTime, symbols * config.timePerSymbol)
        active[player.uniqueId] = Bubble(display, System.currentTimeMillis() + (seconds * 1000).toLong())
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val iterator = active.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, bubble) = iterator.next()
            val player = Bukkit.getPlayer(uuid)
            if (player == null || !player.isOnline || now >= bubble.expiresAtMillis) {
                bubble.display.remove()
                iterator.remove()
                continue
            }
            val target = bubbleLocation(player)
            val current = bubble.display.location
            // Standing still: skip the redundant teleport packet. The world check also guards
            // distanceSquared, which throws on cross-world locations.
            if (current.world === target.world && current.distanceSquared(target) < 1.0e-6) continue
            bubble.display.teleport(target)
        }
    }

    fun clear(player: UUID) {
        active.remove(player)?.display?.remove()
    }

    private fun clearAll() {
        active.values.forEach { it.display.remove() }
        active.clear()
    }

    private fun passesRequirements(player: Player, message: String): Boolean {
        if (config.permission.isNotEmpty() && !player.hasPermission(config.permission)) return false
        if (message.length < config.minSymbols) return false
        if (config.worlds.isNotEmpty() && player.world.name !in config.worlds) return false
        return true
    }

    private fun bubbleLocation(player: Player): Location =
        player.eyeLocation.clone().add(0.0, config.headDistance, 0.0)

    private fun backgroundColor(): Color {
        val c = config.backgroundColor
        return Color.fromARGB(config.backgroundOpacity, c.red, c.green, c.blue)
    }

    private fun render(player: Player, message: String): Component {
        // Format is admin-authored (trusted): resolve {playerName} and PAPI on it first, then splice in
        // the player's text with tags ESCAPED — same anti-injection rule as chat, so a bubble can't
        // carry <click>/colour the sender typed.
        val withName = config.format.replace("{playerName}", player.name)
        val papiApplied = papi.apply(player, withName)
        val full = papiApplied.replace("{message}", mm.escapeTags(message))
        return mm.deserialize(full)
    }
}
