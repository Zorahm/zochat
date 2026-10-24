package zorahm.zochat.say

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.server.ServerCommandEvent
import zorahm.zochat.badge.Badges
import zorahm.zochat.chat.PapiHook
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.guard.CommandGuard

/**
 * Replaces vanilla /say's plain white output with the plugin's MiniMessage formatting so it reads
 * like chat. Vanilla /say is a Brigadier command that never fires the chat events, so we intercept
 * the command itself: [PlayerCommandPreprocessEvent] for players, [ServerCommandEvent] for console,
 * command blocks and RCON. The original is cancelled and we send our own component to every player
 * and the console instead, so nothing is lost.
 */
class SayListener(
    private val config: ChatConfig,
    private val papi: PapiHook,
    private val badges: Badges,
) : Listener {

    private val mm = MiniMessage.miniMessage()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSay(event: PlayerCommandPreprocessEvent) {
        if (!config.isSayFormattingEnabled) return
        // This event fires BEFORE Bukkit checks command permissions, so without this any player
        // could broadcast via /say. Leave a denied /say alone — vanilla answers with its own error.
        if (!event.player.hasPermission(SAY_PERMISSION)) return
        val message = sayArgument(event.message) ?: return
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

    /** Message part of a "/say ..." line, or null if this isn't /say or carries no text. */
    private fun sayArgument(rawLine: String): String? {
        // Reuse the guard's name normalization so "/minecraft:say" is treated like "/say".
        if (CommandGuard.normalize(rawLine, stripNamespace = true) != "say") return null
        val arg = rawLine.trim().substringAfter(' ', "").trim()
        return arg.ifEmpty { null } // no text -> let vanilla show its own usage error
    }

    private fun broadcastFromPlayer(sender: Player, rawMessage: String) {
        var format = config.sayFormat
        // Admin-authored format only (safe): expand %...% with the player as context, like chat.
        if (config.isPlaceholderApiEnabled && config.isPlaceholderApiFormatEnabled) {
            format = papi.apply(sender, format)
        }
        // Player text is untrusted: escape so typed <...> never renders as MiniMessage (no colour
        // or <click:run_command> injection), exactly like the chat pipeline does.
        deliver { tags -> render(format, "", sender.name, mm.escapeTags(rawMessage), tags) }
    }

    private fun broadcastFromConsole(rawMessage: String) {
        // Console/command blocks are trusted: their MiniMessage tags are kept so coloured
        // announcements work — that's the whole point of formatting /say for admins.
        deliver { tags -> render(config.sayFormat, config.consolePrefix, config.consoleName, rawMessage, tags) }
    }

    // Not Bukkit.broadcast(): one component for everyone would hand the console the badge glyph,
    // which it can't draw — the console gets text labels instead.
    private fun deliver(build: (TagResolver) -> Component) {
        val toPlayers = build(badges.forPlayers)
        Bukkit.getOnlinePlayers().forEach { it.sendMessage(toPlayers) }
        Bukkit.getConsoleSender().sendMessage(build(badges.forConsole))
    }

    private fun render(format: String, prefix: String, name: String, message: String, tags: TagResolver): Component {
        // Splice into the format STRING before parsing, not replaceText on the built component: a
        // {player}/{message} sitting inside a <gradient> is split per-character in the component,
        // so replaceText can't find the whole literal token and leaves it raw. Names use the safe
        // account charset, the prefix is admin config and the player message is pre-escaped, so
        // string splicing injects nothing. {message} is substituted last so a {player}/{prefix}
        // typed inside a message stays literal.
        // A format from before {prefix} existed still shows the console prefix — in front.
        val withPrefix = if ("{prefix}" in format) format else "{prefix}$format"
        val spliced = withPrefix
            .replace("{prefix}", prefix)
            .replace("{suffix}", "")
            .replace("{player}", name)
            .replace("{message}", message)
        return mm.deserialize(spliced, tags)
    }

    private companion object {
        const val SAY_PERMISSION = "minecraft.command.say"
    }
}
