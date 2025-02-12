package zorahm.zochat.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PrivateMessageCommand implements CommandExecutor {
    private final ChatPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, UUID> lastMessageMap = new HashMap<>();

    public PrivateMessageCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько игроки могут отправлять ЛС.");
            return true;
        }
        Player senderPlayer = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /msg <игрок> <сообщение>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден.");
            return true;
        }

        String message = String.join(" ", args).substring(args[0].length()).trim();
        String senderFormat = plugin.getConfig().getString("private-message-reply-format", "{player} -> {message}");
        String receiverFormat = plugin.getConfig().getString("private-message-format", "{player}: {message}");

        // Заменяем переменные
        senderFormat = senderFormat.replace("{player}", target.getName()).replace("{message}", message);
        receiverFormat = receiverFormat.replace("{player}", senderPlayer.getName()).replace("{message}", message);

        // Отправляем форматированные сообщения
        senderPlayer.sendMessage(miniMessage.deserialize(senderFormat));
        target.sendMessage(miniMessage.deserialize(receiverFormat));

        lastMessageMap.put(senderPlayer.getUniqueId(), target.getUniqueId());
        lastMessageMap.put(target.getUniqueId(), senderPlayer.getUniqueId());

        return true;
    }

    public UUID getLastMessaged(UUID uuid) {
        return lastMessageMap.get(uuid);
    }
}
