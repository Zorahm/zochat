package zorahm.zochat.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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

public class ChatBanCommand implements CommandExecutor, TabCompleter {
    private final ChatPlugin plugin;
    private final ModerationManager moderationManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatBanCommand(ChatPlugin plugin, ModerationManager moderationManager) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zochat.chatban")) {
            sender.sendMessage(miniMessage.deserialize("<red>У вас нет прав на использование этой команды!</red>"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(miniMessage.deserialize("<yellow>Использование: /chatban <игрок> [причина]</yellow>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(miniMessage.deserialize("<red>Игрок не найден!</red>"));
            return true;
        }

        UUID targetUUID = target.getUniqueId();
        UUID senderUUID = sender instanceof Player ? ((Player) sender).getUniqueId() :
                UUID.fromString("00000000-0000-0000-0000-000000000000");

        String reason = args.length > 1 ?
                String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Не указана";

        if (moderationManager.isChatBanned(targetUUID)) {
            sender.sendMessage(miniMessage.deserialize("<red>Этот игрок уже забанен в чате!</red>"));
            return true;
        }

        // Баним игрока
        moderationManager.chatBanPlayerAsync(targetUUID, senderUUID, reason);

        sender.sendMessage(miniMessage.deserialize(
                "<red>Игрок <white>" + target.getName() + "</white> получил перманентный бан в чате!</red>\n" +
                        "<gray>Причина: " + reason + "</gray>"));

        target.sendMessage(miniMessage.deserialize(
                "<red><bold>ВЫ ЗАБАНЕНЫ В ЧАТЕ!</bold></red>\n" +
                        "<gray>Причина: " + reason + "</gray>\n" +
                        "<yellow>Вы больше не можете писать в чат.</yellow>"));

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
