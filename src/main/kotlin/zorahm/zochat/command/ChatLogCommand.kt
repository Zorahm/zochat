package zorahm.zochat.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.Messages
import zorahm.zochat.storage.ChatLogRepository
import zorahm.zochat.util.PlayerText
import java.util.UUID

class ChatLogCommand(
    private val plugin: Plugin,
    private val chatLog: ChatLogRepository,
    private val messages: Messages
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(messages.component("chatlog.usage"))
            return true
        }

        if (args[0].equals("clear", ignoreCase = true)) {
            if (!sender.hasPermission("zochat.admin")) {
                sender.sendMessage(messages.component("errors.no-permission"))
                return true
            }
            chatLog.clearAll { count ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage(MM.deserialize(messages.get("chatlog.cleared").replace("{count}", count.toString())))
                })
            }
            return true
        }

        val playerName = args[0]
        val online = Bukkit.getPlayerExact(playerName)
        // getOfflinePlayerIfCached avoids the deprecated, main-thread-blocking name lookup.
        val playerUUID: UUID? = online?.uniqueId ?: Bukkit.getOfflinePlayerIfCached(playerName)?.uniqueId
        if (playerUUID == null) {
            sender.sendMessage(messages.component("chatlog.no-messages"))
            return true
        }

        chatLog.recentFor(playerUUID) { list ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val name = Bukkit.getOfflinePlayer(playerUUID).name ?: playerName
                sender.sendMessage(MM.deserialize(messages.get("chatlog.header").replace("{player}", name)))
                if (list.isEmpty()) {
                    sender.sendMessage(messages.component("chatlog.no-messages"))
                } else {
                    list.forEach { msg ->
                        sender.sendMessage(renderLine(messages.get("chatlog.message"), msg))
                    }
                }
            })
        }
        return true
    }

    companion object {
        private val MM = MiniMessage.miniMessage()

        /**
         * One log line. The stored message is the player's raw text, so it's escaped before splicing:
         * otherwise a logged `<click:run_command:...>` renders for the admin reading the log and runs
         * as them on click — an injection aimed squarely at the people with the most permissions.
         */
        @JvmStatic
        fun renderLine(template: String, message: String): Component =
            MM.deserialize(template.replace("{message}", PlayerText.escape(message)))
    }
}
