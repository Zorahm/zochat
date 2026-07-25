package zorahm.zochat.bubble

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import zorahm.zochat.config.Messages

class BubbleCommand(
    private val bubble: BubbleService,
    private val config: BubbleConfig,
    private val messages: Messages,
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messages.component("errors.only-players"))
            return true
        }
        if (!config.isEnabled || !config.trigger.onCommand()) {
            sender.sendMessage(messages.component("bubble.disabled"))
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage(messages.component("bubble.usage"))
            return true
        }
        bubble.onCommand(sender, args.joinToString(" "))
        return true
    }
}
