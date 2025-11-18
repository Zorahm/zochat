package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatPlugin;

public class ClearChatCommand implements CommandExecutor {
    private final ChatPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ClearChatCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zochat.clearchat")) {
            sender.sendMessage(miniMessage.deserialize("<red>У вас нет прав на использование этой команды!</red>"));
            return true;
        }

        // Отправляем 100 пустых строк всем игрокам
        Component emptyLine = Component.text(" ");
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Игроки с правом обхода видят только уведомление
            if (player.hasPermission("zochat.clearchat.bypass")) {
                player.sendMessage(miniMessage.deserialize(
                        "<yellow>Чат был очищен модератором " +
                                (sender instanceof Player ? sender.getName() : "Консоль") + "</yellow>"));
            } else {
                // Остальным отправляем пустые строки
                for (int i = 0; i < 100; i++) {
                    player.sendMessage(emptyLine);
                }
                player.sendMessage(miniMessage.deserialize(
                        "<green><bold>Чат был очищен!</bold></green>"));
            }
        }

        sender.sendMessage(miniMessage.deserialize("<green>Чат успешно очищен для всех игроков!</green>"));
        plugin.getLogger().info("Чат был очищен модератором: " +
                (sender instanceof Player ? sender.getName() : "Консоль"));

        return true;
    }
}
