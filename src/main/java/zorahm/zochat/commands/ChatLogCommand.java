package zorahm.zochat.commands;

import zorahm.zochat.database.DatabaseManager;
import zorahm.zochat.MessageManager;
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
    private final MessageManager messageManager; // Локализация
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatLogCommand(DatabaseManager databaseManager, MessageManager messageManager) {
        this.databaseManager = databaseManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("chatlog.usage")));
            return true;
        }

        if (args[0].equalsIgnoreCase("clear")) {
            if (!sender.hasPermission("chat.admin")) {
                sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("general.no-permission")));
                return true;
            }
            clearChatLogs(sender);
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

            sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("chatlog.header").replace("{player}", playerName)));
            boolean foundMessages = false;

            while (rs.next()) {
                sender.sendMessage(miniMessage.deserialize(
                        messageManager.getMessage("chatlog.message").replace("{message}", rs.getString("message"))
                ));
                foundMessages = true;
            }

            if (!foundMessages) {
                sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("chatlog.no-messages")));
            }
        } catch (SQLException e) {
            sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("chatlog.error")));
            e.printStackTrace();
        }
    }

    private void clearChatLogs(CommandSender sender) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM chat_logs")) {
            int rowsDeleted = stmt.executeUpdate();
            sender.sendMessage(miniMessage.deserialize(
                    messageManager.getMessage("chatlog.cleared").replace("{count}", String.valueOf(rowsDeleted))
            ));
        } catch (SQLException e) {
            sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("chatlog.error")));
            e.printStackTrace();
        }
    }
}
