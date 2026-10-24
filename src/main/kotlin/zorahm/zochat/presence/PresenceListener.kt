package zorahm.zochat.presence

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import zorahm.zochat.chat.CooldownService
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages
import zorahm.zochat.privatemsg.PrivateMessageService
import zorahm.zochat.storage.OfflineMessageRepository
import zorahm.zochat.util.Sounds
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PresenceListener(
    private val plugin: Plugin,
    private val config: ChatConfig,
    private val messages: Messages,
    private val welcome: WelcomeMessages,
    private val offline: OfflineMessageRepository,
    private val pm: PrivateMessageService,
    private val cooldowns: CooldownService
) : Listener {

    private val mm: MiniMessage = MiniMessage.miniMessage()
    private val time: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Only take over the join message when the feature is on — nulling it unconditionally meant
        // the default config (enabled: false) silently removed even the vanilla message.
        if (config.isJoinMessageEnabled) {
            event.joinMessage(null)
            if (!player.hasPermission(config.joinStealthPermission)) {
                Bukkit.getServer().sendMessage(
                    mm.deserialize(messages.get("formats.join").replace("{player}", player.name))
                )
                playToAll(config.joinMessageSound)
            }
        }

        if (welcome.isEnabled) {
            object : BukkitRunnable() {
                override fun run() {
                    // The player may have disconnected during the delay — don't message a ghost.
                    if (!player.isOnline) return
                    for (c in welcome.getWelcomeMessages(player.name)) {
                        player.sendMessage(c)
                    }
                }
            }.runTaskLater(plugin, welcome.delay)
        }

        offline.drainFor(player.uniqueId) { list ->
            // Explicit Runnable: runTask is overloaded on Runnable and Consumer<BukkitTask>, so a bare
            // trailing lambda is ambiguous to the Kotlin resolver.
            Bukkit.getScheduler().runTask(plugin, Runnable {
                // The rows are already deleted; if the player left while the drain was in flight,
                // put them back instead of sending them into the void.
                if (!player.isOnline) {
                    offline.restore(player.uniqueId, list)
                    return@Runnable
                }
                if (list.isNotEmpty()) {
                    player.sendMessage(
                        mm.deserialize(
                            messages.get("chat.offline-messages-header").replace("{count}", list.size.toString())
                        )
                    )
                    for (om in list) {
                        val senderName = Bukkit.getOfflinePlayer(om.sender).name ?: "Unknown"
                        val ts = time.format(Instant.ofEpochMilli(om.sentAtMillis))
                        // Escape the stored sender text — it's the player's literal message and must not be
                        // re-parsed as MiniMessage (would let tags injected into a PM render on delivery).
                        val body = mm.deserialize(mm.escapeTags(om.message))
                            .hoverEvent(
                                HoverEvent.showText(
                                    mm.deserialize(messages.get("chat.message-timestamp").replace("{time}", ts))
                                )
                            )
                        val line = mm.deserialize(messages.get("formats.private-message").replace("{player}", senderName))
                            .replaceText { it.matchLiteral("{message}").replacement(body) }
                        player.sendMessage(line)
                    }
                }
            })
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (config.isQuitMessageEnabled) {
            event.quitMessage(null)
            if (!player.hasPermission(config.quitStealthPermission)) {
                Bukkit.getServer().sendMessage(
                    mm.deserialize(messages.get("formats.quit").replace("{player}", player.name))
                )
                playToAll(config.quitMessageSound)
            }
        }
        cooldowns.clear(player.uniqueId)
        pm.clearLastMessaged(player.uniqueId)
    }

    private fun playToAll(soundName: String) {
        Sounds.parse(soundName).ifPresent { s ->
            Bukkit.getOnlinePlayers().forEach { p -> p.playSound(p.location, s, 1.0f, 1.0f) }
        }
    }
}
