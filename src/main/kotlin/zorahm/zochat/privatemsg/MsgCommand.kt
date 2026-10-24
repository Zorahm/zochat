package zorahm.zochat.privatemsg

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import zorahm.zochat.config.Messages

class MsgCommand(private val pm: PrivateMessageService, private val messages: Messages) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messages.component("errors.only-players"))
            return true
        }
        if (args.size < 2) {
            sender.sendMessage(messages.component("private-messages.usage"))
            return true
        }
        val target = args[0]
        val message = args.drop(1).joinToString(" ")
        pm.send(sender, target, message)
        return true
    }
}
