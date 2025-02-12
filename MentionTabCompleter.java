package zorahm.zochat;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MentionTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Если автодополнение активируется через символ "@"
        if (args.length > 0 && args[args.length - 1].startsWith("@")) {
            String input = args[args.length - 1].substring(1); // Убираем "@"
            List<String> suggestions = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(input.toLowerCase())) {
                    suggestions.add("@" + player.getName());
                }
            }
            return suggestions;
        }

        return null; // Возвращаем пустой список (нет автодополнения)
    }
}
