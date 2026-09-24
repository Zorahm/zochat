package zorahm.zochat.say

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.server.ServerCommandEvent
import org.bukkit.permissions.Permissible
import zorahm.zochat.chat.LuckPermsMeta
import zorahm.zochat.chat.PapiHook
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.guard.CommandGuard
import zorahm.zochat.util.PlayerText

/**
 * Replaces vanilla /say's plain white output with the plugin's MiniMessage formatting so it reads
 * like chat. Vanilla /say is a Brigadier command that never fires the chat events, so we intercept
 * the command itself: [PlayerCommandPreprocessEvent] for players, [ServerCommandEvent] for console,
 * command blocks and RCON. The original is cancelled and we broadcast our own component instead
 * (Bukkit.broadcast reaches every player plus the console, so nothing is lost).
 */
class SayListener(
    private val config: ChatConfig,
    private val papi: PapiHook,
    private val meta: LuckPermsMeta,
) : Listener {

    private val mm = MiniMessage.miniMessage()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSay(event: PlayerCommandPreprocessEvent) {
        if (!config.isSayFormattingEnabled) return
        val message = playerSayText(event.player, event.message) ?: return
        event.isCancelled = true
        broadcastFromPlayer(event.player, message)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onServerSay(event: ServerCommandEvent) {
        if (!config.isSayFormattingEnabled) return
        // ServerCommandEvent fires for every non-player sender (console/command block/RCON); a
        // Player never reaches it, so there's no overlap with onPlayerSay.
        val message = sayArgument(event.command) ?: return
        event.isCancelled = true
        broadcastFromConsole(message)
    }

    private fun broadcastFromPlayer(sender: Player, rawMessage: String) {
        var format = meta.expand(sender, config.sayFormat)
        // Admin-authored format only (safe): expand %...% with the player as context, like chat.
        if (config.isPlaceholderApiEnabled && config.isPlaceholderApiFormatEnabled) {
            format = papi.apply(sender, format)
        }
        // Player text is untrusted: escape so typed <...> never renders as MiniMessage (no colour
        // or <click:run_command> injection), exactly like the chat pipeline does.
        Bukkit.getServer().broadcast(render(format, sender.name, PlayerText.escape(rawMessage)))
    }

    private fun broadcastFromConsole(rawMessage: String) {
        // Console/command blocks are trusted: their MiniMessage tags are kept so coloured
        // announcements work — that's the whole point of formatting /say for admins.
        // The console has no LuckPerms meta: its {meta:...} tokens resolve to nothing.
        val format = LuckPermsMeta.expand(config.sayFormat) { null }
        Bukkit.getServer().broadcast(render(format, config.sayConsoleName, rawMessage))
    }

    private fun render(format: String, name: String, message: String): Component {
        // Splice into the format STRING before parsing, not replaceText on the built component: a
        // {player}/{message} sitting inside a <gradient> is split per-character in the component,
        // so replaceText can't find the whole literal token and leaves it raw. Names use the safe
        // account charset and the player message is pre-escaped, so string splicing injects nothing.
        // {message} is substituted last so a {player}/{prefix} typed inside a message stays literal.
        val spliced = format
            .replace("{prefix}", "")
            .replace("{suffix}", "")
            .replace("{player}", name)
            .replace("{message}", message)
        return mm.deserialize(spliced)
    }

    companion object {
        // Vanilla's own node for /say. We cancel the command and broadcast ourselves, so vanilla's
        // permission check never runs — without this gate any player could broadcast server-wide.
        const val SAY_PERMISSION = "minecraft.command.say"

        /** Message part of a "/say ..." line, or null if this isn't /say or carries no text. */
        private fun sayArgument(rawLine: String): String? {
            // Reuse the guard's name normalization so "/minecraft:say" is treated like "/say".
            if (CommandGuard.normalize(rawLine, stripNamespace = true) != "say") return null
            val arg = rawLine.trim().substringAfter(' ', "").trim()
            return arg.ifEmpty { null } // no text -> let vanilla show its own usage error
        }

        /**
         * Text to broadcast for a player's "/say ..." line, or null to leave the command to vanilla —
         * including when the player lacks [SAY_PERMISSION], so vanilla rejects it with its usual error.
         */
        @JvmStatic
        fun playerSayText(player: Permissible, rawLine: String): String? {
            val text = sayArgument(rawLine) ?: return null
            return if (player.hasPermission(SAY_PERMISSION)) text else null
        }
    }
}
