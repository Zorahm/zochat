package zorahm.zochat.chat

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages

class LocalCommand(
    private val chat: ChatListener,
    private val config: ChatConfig,
    private val messages: Messages
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messages.component("errors.only-players"))
            return true
        }
        if (!config.isLocalChatEnabled) {
            sender.sendMessage(messages.component("chat.local-disabled"))
            return true
        }
        if (args.isEmpty()) return true
        // Through the chat event rather than straight to ChatService, so mute plugins see it.
        chat.chatAs(sender, args.joinToString(" "), ChatListener.Forced.LOCAL)
        return true
    }
}
