package zorahm.zochat.command

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import zorahm.zochat.ZoChatPlugin
import zorahm.zochat.badge.Badges
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages

class ChatCommand(
    private val plugin: ZoChatPlugin,
    private val config: ChatConfig,
    private val messages: Messages,
    private val badges: Badges
) : TabExecutor {

    private val mm: MiniMessage = MiniMessage.miniMessage()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", ignoreCase = true)) {
            val helpMessages = messages.getList("chat.chat-help")
            helpMessages.forEach { sender.sendMessage(it) }
            return true
        }

        if (args[0].equals("reload", ignoreCase = true)) {
            if (!sender.hasPermission(ADMIN)) {
                sender.sendMessage(messages.component("errors.no-permission"))
                return true
            }
            plugin.reloadEverything()
            sender.sendMessage(mm.deserialize(config.messagePrefix + messages.get("chat.reload-success")))
            return true
        }

        if (args[0].equals("consoleprefix", ignoreCase = true)) {
            consolePrefix(sender, args.drop(1))
            return true
        }

        sender.sendMessage(mm.deserialize(config.messagePrefix + messages.get("chat.unknown-subcommand")))
        return true
    }

    // /chat consoleprefix               -> show the current one
    // /chat consoleprefix clear         -> remove it
    // /chat consoleprefix <MiniMessage> -> set it, e.g. <badge:console>
    private fun consolePrefix(sender: CommandSender, args: List<String>) {
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage(messages.component("errors.no-permission"))
            return
        }
        if (args.isNotEmpty()) {
            val raw = args.joinToString(" ")
            // Command arguments lose trailing spaces, so the gap before the console's name is added here.
            config.setConsolePrefix(if (raw.equals("clear", ignoreCase = true)) "" else "$raw ")
        }
        val key = when {
            config.consolePrefix.isEmpty() -> if (args.isEmpty()) "console-prefix.none" else "console-prefix.cleared"
            args.isEmpty() -> "console-prefix.current"
            else -> "console-prefix.set"
        }
        // The preview is the console exactly as players (or, for the console itself, the log) see it.
        val tags = if (sender is Player) badges.forPlayers else badges.forConsole
        val preview = mm.deserialize(config.consoleDisplay, tags)
        sender.sendMessage(
            mm.deserialize(config.messagePrefix + messages.get(key))
                .replaceText { it.matchLiteral("{preview}").replacement(preview) }
        )
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val options = when {
            args.size == 1 -> if (sender.hasPermission(ADMIN)) listOf("help", "reload", "consoleprefix") else listOf("help")
            args.size == 2 && args[0].equals("consoleprefix", ignoreCase = true) && sender.hasPermission(ADMIN) ->
                listOf("<badge:console>", "clear")
            else -> emptyList()
        }
        return options.filter { it.startsWith(args.last(), ignoreCase = true) }
    }

    private companion object {
        const val ADMIN = "zochat.admin"
    }
}
