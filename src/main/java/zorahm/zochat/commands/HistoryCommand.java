package zorahm.zochat.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatPlugin;
import zorahm.zochat.MessageHistoryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HistoryCommand implements CommandExecutor, TabCompleter {
    private final ChatPlugin plugin;
    private final MessageHistoryManager historyManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public HistoryCommand(ChatPlugin plugin, MessageHistoryManager historyManager) {
        this.plugin = plugin;
        this.historyManager = historyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Эта команда доступна только для игроков!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(miniMessage.deserialize(
                    "<yellow>Использование: /history <игрок></yellow>"));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>Вы не можете просмотреть историю сообщений с самим собой!</red>"));
            return true;
        }

        // Показываем историю сообщений
        historyManager.showHistory(player, target.getUniqueId());

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
