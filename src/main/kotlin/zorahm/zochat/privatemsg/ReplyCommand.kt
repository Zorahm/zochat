package zorahm.zochat.privatemsg

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import zorahm.zochat.config.Messages

class ReplyCommand(private val pm: PrivateMessageService, private val messages: Messages) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(messages.component("reply.usage"))
            return true
        }
        val message = args.joinToString(" ")
        if (sender is Player) pm.reply(sender, message) else pm.replyFromConsole(sender, message)
        return true
    }
}
