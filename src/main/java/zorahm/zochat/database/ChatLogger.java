package zorahm.zochat.database;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import zorahm.zochat.ChatConfig;
import zorahm.zochat.ChatPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class ChatLogger {
    private final JavaPlugin plugin;
    private final ChatConfig config;
    private Connection connection;

    public ChatLogger(ChatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getChatConfig();
        initializeDatabase();
    }

    private void initializeDatabase() {
        String dbType = config.getDatabaseType().toLowerCase();
        try {
            if (dbType.equals("mysql")) {
                String url = "jdbc:mysql://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/" + config.getDatabaseName();
                connection = DriverManager.getConnection(url, config.getDatabaseUsername(), config.getDatabasePassword());
            } else {
                String url = "jdbc:sqlite:" + plugin.getDataFolder() + "/chat.db";
                connection = DriverManager.getConnection(url);
            }
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось подключиться к базе данных!", e);
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "message TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS offline_messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "sender_uuid TEXT NOT NULL," +
                    "receiver_uuid TEXT NOT NULL," +
                    "message TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось создать таблицы в базе данных!", e);
        }
    }

    public void logMessage(Player player, String message) {
        String sql = "INSERT INTO chat_logs (player_uuid, message) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, message);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить сообщение в базе данных!", e);
        }
    }

    public void saveOfflineMessage(UUID sender, UUID receiver, String message) {
        String sql = "INSERT INTO offline_messages (sender_uuid, receiver_uuid, message) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sender.toString());
            stmt.setString(2, receiver.toString());
            stmt.setString(3, message);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить оффлайн-сообщение!", e);
        }
    }

    public List<OfflineMessage> getOfflineMessages(UUID receiver) {
        List<OfflineMessage> messages = new ArrayList<>();
        String sql = "SELECT sender_uuid, message, timestamp FROM offline_messages WHERE receiver_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, receiver.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID sender = UUID.fromString(rs.getString("sender_uuid"));
                String message = rs.getString("message");
                Timestamp timestamp = rs.getTimestamp("timestamp");
                messages.add(new OfflineMessage(sender, message, timestamp));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить оффлайн-сообщения!", e);
        }
        return messages;
    }

    public void deleteOfflineMessages(UUID receiver) {
        String sql = "DELETE FROM offline_messages WHERE receiver_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, receiver.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось удалить оффлайн-сообщения!", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось закрыть соединение с базой данных!", e);
        }
    }

    public static class OfflineMessage {
        private final UUID sender;
        private final String message;
        private final Timestamp timestamp;

        public OfflineMessage(UUID sender, String message, Timestamp timestamp) {
            this.sender = sender;
            this.message = message;
            this.timestamp = timestamp;
        }

        public UUID getSender() {
            return sender;
        }

        public String getMessage() {
            return message;
        }

        public Timestamp getTimestamp() {
            return timestamp;
        }
    }
}