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
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import zorahm.zochat.chat.LuckPermsMeta
import zorahm.zochat.chat.PapiHook
import java.util.UUID

/**
 * Floating chat bubbles (TextDisplay above the head). The display RIDES the player as a passenger, so the
 * client moves it together with the player model: smooth, no lag behind the head. It used to be a free
 * entity teleported to the head every tick, which the client could only chase in visible jumps.
 * A repeating main-thread task expires bubbles and re-seats one that got knocked off (teleports eject
 * passengers); if another plugin forbids the mount, the bubble falls back to per-tick teleports.
 */
class BubbleService(
    private val plugin: Plugin,
    private val config: BubbleConfig,
    private val papi: PapiHook,
    private val meta: LuckPermsMeta,
) : Listener {
    private val mm = MiniMessage.miniMessage()
    private val active = HashMap<UUID, Bubble>()
    private var task: BukkitTask? = null

    private class Bubble(val display: TextDisplay, val expiresAtMillis: Long, var mounted: Boolean)

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

        val display = player.world.spawn(anchor(player), TextDisplay::class.java) { d ->
            d.isPersistent = false
            d.text(render(player, message))
            d.billboard = config.billboard
            d.isSeeThrough = config.seeThrough
            d.isShadowed = config.textShadow
            d.lineWidth = config.lineWidth
            d.backgroundColor = backgroundColor()
            // Only matters in the teleport fallback (mount refused): smooths the per-tick teleports a bit.
            d.teleportDuration = 2
            // The height above the head is a translation, not a position: a passenger always sits exactly
            // on the vehicle's attachment point (top of the head, lower while sneaking).
            d.transformation = Transformation(
                Vector3f(0f, config.headDistance.toFloat(), 0f),
                AxisAngle4f(),
                Vector3f(config.scale, config.scale, config.scale),
                AxisAngle4f(),
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
        val mounted = player.addPassenger(display)
        active[player.uniqueId] = Bubble(display, System.currentTimeMillis() + (seconds * 1000).toLong(), mounted)
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val iterator = active.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, bubble) = iterator.next()
            val player = Bukkit.getPlayer(uuid)
            val display = bubble.display
            if (player == null || !player.isOnline || player.isDead || now >= bubble.expiresAtMillis ||
                !display.isValid || display.world != player.world
            ) {
                display.remove()
                iterator.remove()
                continue
            }
            if (bubble.mounted) {
                if (display.vehicle == player) continue
                // Knocked off by a same-world teleport (Paper ejects passengers): walk it over and re-seat.
                display.leaveVehicle()
                display.teleport(anchor(player))
                bubble.mounted = player.addPassenger(display)
                continue
            }
            follow(player, display)
        }
    }

    // Fallback when the mount was refused (another plugin cancelled EntityMountEvent): the old per-tick
    // teleport, which works everywhere but visibly trails the player.
    private fun follow(player: Player, display: TextDisplay) {
        val target = anchor(player)
        val current = display.location
        // Standing still: skip the redundant teleport packet. The world check also guards
        // distanceSquared, which throws on cross-world locations.
        if (current.world === target.world && current.distanceSquared(target) < 1.0e-6) return
        display.teleport(target)
    }

    // Leaving the world with a passenger can make the server carry over a COPY of the display that we hold
    // no reference to — it would ride the player forever. Drop the bubble before the jump instead.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (event.to.world != event.from.world) clear(event.player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        clear(event.player.uniqueId)
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

    // Where a passenger sits: top of the player's bounding box. head-distance is added by the translation.
    private fun anchor(player: Player): Location = player.location.add(0.0, player.height, 0.0)

    private fun backgroundColor(): Color {
        val c = config.backgroundColor
        return Color.fromARGB(config.backgroundOpacity, c.red, c.green, c.blue)
    }

    private fun render(player: Player, message: Component): Component {
        // Format is admin-authored (trusted): resolve {playerName} and PAPI on it first. The message goes
        // in as an inserted component, never as MiniMessage text, so the sender can't inject <click>/colour.
        val withName = meta.expand(player, config.format).replace("{playerName}", player.name)
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
