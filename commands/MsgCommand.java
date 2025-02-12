package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatPlugin;

import java.util.HashMap;
import java.util.UUID;

public class MsgCommand implements CommandExecutor {
    private final ChatPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final HashMap<UUID, UUID> lastMessages = new HashMap<>();

    public MsgCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭту команду может использовать только игрок.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /msg <игрок> <сообщение>");
            return true;
        }

        Player player = (Player) sender;
        String targetName = args[0];
        String message = String.join(" ", args).substring(targetName.length() + 1);

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(miniMessage.deserialize("<red>Игрок с ником <bold>" + targetName + "</bold> не найден или не в сети.</red>"));
            return true;
        }

        // Получаем формат из конфигурации
        String incomingFormat = plugin.getConfig().getString("private-messages.format",
                "<#d45079>SkyPvP</#d45079> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{player} <#c0c0c0>›</#c0c0c0> {message}");
        String outgoingFormat = plugin.getConfig().getString("private-messages.reply-format",
                "<#d45079>SkyPvP</#d45079> <#c0c0c0>•</#c0c0c0> <#fcfcfc>Вы <#c0c0c0>›</#c0c0c0> {message}");

        // Форматируем входящее и исходящее сообщение
        Component incomingMessage = miniMessage.deserialize(incomingFormat
                .replace("{player}", player.getName())
                .replace("{message}", message));

        Component outgoingMessage = miniMessage.deserialize(outgoingFormat
                .replace("{player}", target.getName())
                .replace("{message}", message));

        // Отправляем сообщения
        target.sendMessage(incomingMessage);
        player.sendMessage(outgoingMessage);

        // Обновляем последнее сообщение для команды /reply
        lastMessages.put(target.getUniqueId(), player.getUniqueId());

        return true;
    }

    public HashMap<UUID, UUID> getLastMessages() {
        return lastMessages;
    }
}
