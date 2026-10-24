package zorahm.zochat.chat

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class MentionTabCompleter(private val mentionHandler: MentionHandler) : TabCompleter {

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.isEmpty()) {
            return ArrayList()
        }

        val lastArg = args[args.size - 1]

        if (lastArg.startsWith("@")) {
            val partial = lastArg.substring(1)
            val suggestions = mentionHandler.getPlayerSuggestions(partial, sender)

            val result = ArrayList<String>()
            for (suggestion in suggestions) {
                result.add("@$suggestion")
            }
            return result
        }

        return ArrayList()
    }
}
