package zorahm.zochat.bubble

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffectType
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

    /** [message] is the chat pipeline's processed component: filtered, mentions and ^placeholders rendered. */
    fun onChat(player: Player, message: Component) {
        if (config.isEnabled && config.trigger.onChat()) show(player, message)
    }

    fun onCommand(player: Player, message: String) {
        // Component.text never parses its content, so the player's text can't inject tags.
        if (config.isEnabled && config.trigger.onCommand()) show(player, Component.text(message))
    }

    private fun show(player: Player, message: Component) {
        val plainMessage = PLAIN.serialize(message)
        if (!passesRequirements(player, plainMessage)) return
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
        // A vanish plugin hides the player via hidePlayer — hide the bubble from the same viewers, or it
        // floats where an invisible player stands.
        for (viewer in Bukkit.getOnlinePlayers()) {
            if (viewer != player && !viewer.canSee(player)) viewer.hideEntity(plugin, display)
        }

        val symbols = plainMessage.length
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
        if (isHidden(player)) return false
        if (config.permission.isNotEmpty() && !player.hasPermission(config.permission)) return false
        if (message.length < config.minSymbols) return false
        if (config.worlds.isNotEmpty() && player.world.name !in config.worlds) return false
        return true
    }

    // No bubble for players others aren't meant to see: it would give away exactly where they are.
    // "vanished" metadata is the convention shared by SuperVanish, PremiumVanish and Essentials.
    private fun isHidden(player: Player): Boolean =
        player.gameMode == GameMode.SPECTATOR ||
            player.isInvisible ||
            player.hasPotionEffect(PotionEffectType.INVISIBILITY) ||
            player.getMetadata("vanished").any { it.asBoolean() }

    private fun bubbleLocation(player: Player): Location =
        player.eyeLocation.clone().add(0.0, config.headDistance, 0.0)

    private fun backgroundColor(): Color {
        val c = config.backgroundColor
        return Color.fromARGB(config.backgroundOpacity, c.red, c.green, c.blue)
    }

    private fun render(player: Player, message: Component): Component {
        // Format is admin-authored (trusted): resolve {playerName} and PAPI on it first. The message goes
        // in as an inserted component, never as MiniMessage text, so the sender can't inject <click>/colour.
        val withName = config.format.replace("{playerName}", player.name)
        val papiApplied = papi.apply(player, withName)
        return mm.deserialize(
            papiApplied.replace("{message}", "<zochat_message>"),
            Placeholder.component("zochat_message", message),
        )
    }

    private companion object {
        val PLAIN: PlainTextComponentSerializer = PlainTextComponentSerializer.plainText()
    }
}
