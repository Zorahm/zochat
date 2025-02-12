package zorahm.zochat.commands;

import zorahm.zochat.database.DatabaseManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ChatLogCommand implements CommandExecutor {
    private final DatabaseManager databaseManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatLogCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(miniMessage.deserialize("<yellow>Использование: /chatlog <игрок> | clear</yellow>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("clear")) {
            if (!sender.hasPermission("chat.admin")) {
                sender.sendMessage(miniMessage.deserialize("<red>У вас нет прав!</red>"));
                return true;
            }
            clearChatLogs();
            sender.sendMessage(miniMessage.deserialize("<green>Все логи чата удалены!</green>"));
            return true;
        }

        String playerName = args[0];
        getChatLogs(sender, playerName);

        return true;
    }

    private void getChatLogs(CommandSender sender, String playerName) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT message FROM chat_logs WHERE player = ? ORDER BY timestamp DESC LIMIT 10")) {
            stmt.setString(1, playerName);
            ResultSet rs = stmt.executeQuery();

            sender.sendMessage(miniMessage.deserialize("<yellow>Последние сообщения " + playerName + ":</yellow>"));
            while (rs.next()) {
                sender.sendMessage(miniMessage.deserialize("<gray>" + rs.getString("message") + "</gray>"));
            }
        } catch (SQLException e) {
            sender.sendMessage(miniMessage.deserialize("<red>Ошибка получения логов!</red>"));
            e.printStackTrace();
        }
    }

    private void clearChatLogs() {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM chat_logs")) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
