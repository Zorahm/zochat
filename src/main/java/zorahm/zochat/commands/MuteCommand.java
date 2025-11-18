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

public class MuteCommand implements CommandExecutor, TabCompleter {
    private final ChatPlugin plugin;
    private final ModerationManager moderationManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MuteCommand(ChatPlugin plugin, ModerationManager moderationManager) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zochat.mute")) {
            sender.sendMessage(miniMessage.deserialize("<red>У вас нет прав на использование этой команды!</red>"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(miniMessage.deserialize("<yellow>Использование: /mute <игрок> [длительность(сек)] [причина]</yellow>"));
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

        // Парсим длительность (в секундах), по умолчанию 0 = бесконечно
        long durationMillis = 0;
        String reason = "Не указана";
        int reasonStartIndex = 1;

        if (args.length >= 2) {
            try {
                int durationSeconds = Integer.parseInt(args[1]);
                durationMillis = durationSeconds * 1000L;
                reasonStartIndex = 2;
            } catch (NumberFormatException e) {
                // Это не число, значит это причина
                reasonStartIndex = 1;
            }
        }

        // Собираем причину из оставшихся аргументов
        if (args.length > reasonStartIndex) {
            reason = String.join(" ", java.util.Arrays.copyOfRange(args, reasonStartIndex, args.length));
        }

        // Мьютим игрока
        moderationManager.mutePlayerAsync(targetUUID, senderUUID, reason, durationMillis);

        String durationText = durationMillis > 0 ?
                " на " + (durationMillis / 1000) + " секунд" : " навсегда";

        sender.sendMessage(miniMessage.deserialize(
                "<green>Игрок <white>" + target.getName() + "</white> был замьючен" + durationText + "!</green>"));

        target.sendMessage(miniMessage.deserialize(
                "<red><bold>Вы были замьючены!</bold></red>\n" +
                        "<gray>Причина: " + reason + "</gray>\n" +
                        "<gray>Длительность: " + (durationMillis > 0 ? (durationMillis / 1000) + " секунд" : "Бессрочно") + "</gray>"));

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
