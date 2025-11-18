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
import zorahm.zochat.IgnoreManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class UnignoreCommand implements CommandExecutor, TabCompleter {
    private final ChatPlugin plugin;
    private final IgnoreManager ignoreManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public UnignoreCommand(ChatPlugin plugin, IgnoreManager ignoreManager) {
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
                    "<yellow>Использование: /unignore <игрок></yellow>"));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        if (!ignoreManager.isIgnoring(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>Вы не игнорируете этого игрока!</red>"));
            return true;
        }

        ignoreManager.removeIgnore(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(miniMessage.deserialize(
                "<green>Вы больше не игнорируете игрока <white>" + target.getName() + "</white>!</green>"));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender instanceof Player) {
            Player player = (Player) sender;
            Set<UUID> ignored = ignoreManager.getIgnoredPlayers(player.getUniqueId());

            return ignored.stream()
                    .map(Bukkit::getOfflinePlayer)
                    .map(OfflinePlayer::getName)
                    .filter(name -> name != null && name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
