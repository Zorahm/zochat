package zorahm.zochat.guard

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerCommandSendEvent
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.Messages
import zorahm.zochat.util.Sounds

/**
 * Blocks console-only commands (e.g. /op, /seed) from being run by players. The events here fire
 * only for players — console and command blocks never reach [PlayerCommandPreprocessEvent], so they
 * are never affected. One permission ([BYPASS_PERMISSION]) exempts trusted players from all of it.
 */
class CommandGuardListener(
    private val plugin: Plugin,
    private val config: CommandGuardConfig,
    private val messages: Messages,
) : Listener {

    // HIGHEST + ignoreCancelled so we decide after other plugins but before the command runs.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (!config.isEnabled) return
        val player = event.player
        if (player.hasPermission(BYPASS_PERMISSION)) return
        if (!config.guard.isBlocked(event.message, ::namesOf)) return

        event.isCancelled = true
        if (config.notifyMessage) player.sendMessage(messages.component("command-guard.blocked"))
        if (config.playSound) {
            Sounds.parse(config.sound).ifPresent { player.playSound(player.location, it, 1.0f, 1.0f) }
        }
        if (config.consoleLog) {
            // Server-facing audit line — English, like the rest of the plugin's logger output.
            plugin.logger.info("${player.name} tried to run blocked command: ${event.message.trim()}")
        }
    }

    // Strip blocked commands from client-side suggestions so players never even see them.
    @EventHandler(ignoreCancelled = true)
    fun onCommandSend(event: PlayerCommandSendEvent) {
        if (!config.isEnabled || !config.hideFromTab) return
        if (event.player.hasPermission(BYPASS_PERMISSION)) return
        // getCommands() is a mutable view per the Paper API; removing drops the entry (and its
        // namespaced twin, since isBlocked normalizes) from the suggestion list.
        event.commands.removeIf { config.guard.isBlocked(it, ::namesOf) }
    }

    private fun namesOf(label: String): Collection<String> {
        val command = Bukkit.getCommandMap().getCommand(label) ?: return emptyList()
        return listOf(command.name) + command.aliases
    }

    private companion object {
        const val BYPASS_PERMISSION = "zochat.command.bypass"
    }
}
