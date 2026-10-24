package zorahm.zochat.command

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import zorahm.zochat.ZoChatPlugin
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages

class ChatCommand(
    private val plugin: ZoChatPlugin,
    private val config: ChatConfig,
    private val messages: Messages
) : CommandExecutor {

    private val mm: MiniMessage = MiniMessage.miniMessage()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", ignoreCase = true)) {
            val helpMessages = messages.getList("chat.chat-help")
            helpMessages.forEach { sender.sendMessage(it) }
            return true
        }

        if (args[0].equals("reload", ignoreCase = true)) {
            if (!sender.hasPermission("zochat.admin")) {
                sender.sendMessage(messages.component("errors.no-permission"))
                return true
            }
            plugin.reloadEverything()
            sender.sendMessage(mm.deserialize(config.messagePrefix + messages.get("chat.reload-success")))
            return true
        }

        sender.sendMessage(mm.deserialize(config.messagePrefix + messages.get("chat.unknown-subcommand")))
        return true
    }
}
