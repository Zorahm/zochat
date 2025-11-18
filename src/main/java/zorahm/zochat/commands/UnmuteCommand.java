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
import zorahm.zochat.ModerationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class UnmuteCommand implements CommandExecutor, TabCompleter {
    private final ChatPlugin plugin;
    private final ModerationManager moderationManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public UnmuteCommand(ChatPlugin plugin, ModerationManager moderationManager) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zochat.unmute")) {
            sender.sendMessage(miniMessage.deserialize("<red>У вас нет прав на использование этой команды!</red>"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(miniMessage.deserialize("<yellow>Использование: /unmute <игрок></yellow>"));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID targetUUID = target.getUniqueId();

        if (!moderationManager.isMuted(targetUUID)) {
            sender.sendMessage(miniMessage.deserialize("<red>Этот игрок не замьючен!</red>"));
            return true;
        }

        moderationManager.unmutePlayerAsync(targetUUID);
        sender.sendMessage(miniMessage.deserialize(
                "<green>Игрок <white>" + target.getName() + "</white> был размьючен!</green>"));

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            onlineTarget.sendMessage(miniMessage.deserialize(
                    "<green>Вы были размьючены! Теперь вы можете писать в чат.</green>"));
        }

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
