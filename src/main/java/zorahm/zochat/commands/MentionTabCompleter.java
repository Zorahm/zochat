package zorahm.zochat.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import zorahm.zochat.MentionHandler;

import java.util.ArrayList;
import java.util.List;

public class MentionTabCompleter implements TabCompleter {
    private final MentionHandler mentionHandler;

    public MentionTabCompleter(MentionHandler mentionHandler) {
        this.mentionHandler = mentionHandler;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return new ArrayList<>();
        }

        String lastArg = args[args.length - 1];
        if (lastArg.startsWith("@")) {
            String partial = lastArg.substring(1);
            List<String> suggestions = mentionHandler.getPlayerSuggestions(partial);
            List<String> result = new ArrayList<>();
            for (String suggestion : suggestions) {
                result.add("@" + suggestion);
            }
            return result;
        }

        return new ArrayList<>();
    }
}
