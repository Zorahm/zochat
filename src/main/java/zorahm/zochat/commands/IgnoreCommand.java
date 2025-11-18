package zorahm.zochat.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatPlugin;
import zorahm.zochat.IgnoreManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class IgnoreCommand implements CommandExecutor, TabCompleter {
    private final ChatPlugin plugin;
    private final IgnoreManager ignoreManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public IgnoreCommand(ChatPlugin plugin, IgnoreManager ignoreManager) {
        this.plugin = plugin;
        this.ignoreManager = ignoreManager;
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
                    "<yellow>Использование: /ignore <игрок></yellow>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(miniMessage.deserialize("<red>Игрок не найден!</red>"));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>Вы не можете игнорировать самого себя!</red>"));
            return true;
        }

        if (ignoreManager.isIgnoring(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize(
                    "<yellow>Вы уже игнорируете игрока <white>" + target.getName() + "</white>!</yellow>"));
            return true;
        }

        ignoreManager.addIgnore(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(miniMessage.deserialize(
                "<green>Теперь вы игнорируете игрока <white>" + target.getName() + "</white>!</green>\n" +
                        "<gray>Вы не будете видеть его сообщения в чате и личные сообщения.</gray>"));

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
