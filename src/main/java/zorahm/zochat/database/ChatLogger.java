package zorahm.zochat.database;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import zorahm.zochat.ChatConfig;
import zorahm.zochat.ChatPlugin;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class ChatLogger {
    private final JavaPlugin plugin;
    private final ChatConfig config;
    private Connection connection;
    private static final int MAX_HISTORY_SIZE = 10;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

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
            // Таблица логов чата
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "message TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");

            // Таблица оффлайн сообщений
            stmt.execute("CREATE TABLE IF NOT EXISTS offline_messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "sender_uuid TEXT NOT NULL," +
                    "receiver_uuid TEXT NOT NULL," +
                    "message TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");

            // Таблица истории личных сообщений
            stmt.execute("CREATE TABLE IF NOT EXISTS private_message_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "sender_uuid TEXT NOT NULL," +
                    "receiver_uuid TEXT NOT NULL," +
                    "message TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");

            // Таблица мьютов
            stmt.execute("CREATE TABLE IF NOT EXISTS mutes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "muted_by TEXT NOT NULL," +
                    "reason TEXT," +
                    "start_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "end_time DATETIME," +
                    "active INTEGER DEFAULT 1)");

            // Таблица варнингов
            stmt.execute("CREATE TABLE IF NOT EXISTS warnings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "warned_by TEXT NOT NULL," +
                    "reason TEXT," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");

            // Таблица чат-банов
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_bans (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "banned_by TEXT NOT NULL," +
                    "reason TEXT," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "active INTEGER DEFAULT 1)");

            // Таблица активности игроков
            stmt.execute("CREATE TABLE IF NOT EXISTS player_activity (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "player_name TEXT NOT NULL," +
                    "last_seen DATETIME DEFAULT CURRENT_TIMESTAMP)");

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

    // ═══════════════════════════════════════════════════════════════════
    // АСИНХРОННЫЕ МЕТОДЫ ДЛЯ ИСТОРИИ ЛИЧНЫХ СООБЩЕНИЙ
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Асинхронно сохраняет личное сообщение в историю
     */
    public void savePrivateMessageAsync(UUID sender, UUID receiver, String message) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO private_message_history (sender_uuid, receiver_uuid, message) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, sender.toString());
                stmt.setString(2, receiver.toString());
                stmt.setString(3, message);
                stmt.executeUpdate();

                // Удаляем старые сообщения, оставляя только последние 10 для каждой пары игроков
                cleanOldPrivateMessages(sender, receiver);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить личное сообщение в историю!", e);
            }
        });
    }

    /**
     * Очищает старые сообщения, оставляя только MAX_HISTORY_SIZE последних
     */
    private void cleanOldPrivateMessages(UUID player1, UUID player2) {
        String sql = "DELETE FROM private_message_history WHERE id NOT IN (" +
                "SELECT id FROM private_message_history WHERE " +
                "(sender_uuid = ? AND receiver_uuid = ?) OR (sender_uuid = ? AND receiver_uuid = ?) " +
                "ORDER BY timestamp DESC LIMIT ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, player1.toString());
            stmt.setString(2, player2.toString());
            stmt.setString(3, player2.toString());
            stmt.setString(4, player1.toString());
            stmt.setInt(5, MAX_HISTORY_SIZE);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось очистить старые сообщения из истории!", e);
        }
    }

    /**
     * Получает историю личных сообщений между двумя игроками (синхронно, для отображения)
     */
    public List<PrivateMessageHistory> getPrivateMessageHistory(UUID player1, UUID player2) {
        List<PrivateMessageHistory> history = new ArrayList<>();
        String sql = "SELECT sender_uuid, receiver_uuid, message, timestamp FROM private_message_history " +
                "WHERE (sender_uuid = ? AND receiver_uuid = ?) OR (sender_uuid = ? AND receiver_uuid = ?) " +
                "ORDER BY timestamp ASC LIMIT ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, player1.toString());
            stmt.setString(2, player2.toString());
            stmt.setString(3, player2.toString());
            stmt.setString(4, player1.toString());
            stmt.setInt(5, MAX_HISTORY_SIZE);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID sender = UUID.fromString(rs.getString("sender_uuid"));
                UUID receiver = UUID.fromString(rs.getString("receiver_uuid"));
                String message = rs.getString("message");
                Timestamp timestamp = rs.getTimestamp("timestamp");
                history.add(new PrivateMessageHistory(sender, receiver, message, timestamp));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить историю сообщений!", e);
        }
        return history;
    }

    // ═══════════════════════════════════════════════════════════════════
    // АСИНХРОННЫЕ МЕТОДЫ ДЛЯ АКТИВНОСТИ ИГРОКОВ
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Асинхронно обновляет время последней активности игрока
     */
    public void updatePlayerActivityAsync(UUID playerUUID, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT OR REPLACE INTO player_activity (player_uuid, player_name, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось обновить активность игрока!", e);
            }
        });
    }

    /**
     * Получает время последней активности игрока (синхронно)
     */
    public Timestamp getPlayerLastSeen(UUID playerUUID) {
        String sql = "SELECT last_seen FROM player_activity WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getTimestamp("last_seen");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось получить время последней активности игрока!", e);
        }
        return null;
    }

    /**
     * Получает соединение с базой данных для других менеджеров
     */
    public Connection getConnection() {
        return connection;
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

    /**
     * Класс для хранения истории личных сообщений
     */
    public static class PrivateMessageHistory {
        private final UUID sender;
        private final UUID receiver;
        private final String message;
        private final Timestamp timestamp;

        public PrivateMessageHistory(UUID sender, UUID receiver, String message, Timestamp timestamp) {
            this.sender = sender;
            this.receiver = receiver;
            this.message = message;
            this.timestamp = timestamp;
        }

        public UUID getSender() {
            return sender;
        }

        public UUID getReceiver() {
            return receiver;
        }

        public String getMessage() {
            return message;
        }

        public Timestamp getTimestamp() {
            return timestamp;
        }

        public String getFormattedTime() {
            return timestamp.toLocalDateTime().format(TIME_FORMATTER);
        }
    }
}