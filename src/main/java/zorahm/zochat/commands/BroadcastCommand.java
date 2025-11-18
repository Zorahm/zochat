package zorahm.zochat.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import zorahm.zochat.ChatPlugin;

public class BroadcastCommand implements CommandExecutor {
    private final ChatPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public BroadcastCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zochat.broadcast")) {
            sender.sendMessage(miniMessage.deserialize("<red>У вас нет прав на использование этой команды!</red>"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(miniMessage.deserialize(
                    "<yellow>Использование: /broadcast <сообщение></yellow>"));
            return true;
        }

        String message = String.join(" ", args);

        // Отправляем объявление всем игрокам
        Bukkit.broadcast(miniMessage.deserialize(
                "<gradient:#ff6b6b:#4ecdc4>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>\n" +
                        "<yellow><bold>📢 ОБЪЯВЛЕНИЕ:</bold></yellow>\n" +
                        "<white>" + message + "</white>\n" +
                        "<gradient:#ff6b6b:#4ecdc4>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>"));

        sender.sendMessage(miniMessage.deserialize("<green>Объявление успешно отправлено!</green>"));
        plugin.getLogger().info("Объявление от " + sender.getName() + ": " + message);

        return true;
    }
}
