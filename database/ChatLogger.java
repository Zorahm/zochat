package zorahm.zochat.database;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ChatLogger {
    private final DatabaseManager databaseManager;

    public ChatLogger(JavaPlugin plugin) {
        this.databaseManager = new DatabaseManager(plugin);
    }

    public void logMessage(Player player, String message) {
        String sql = "INSERT INTO chat_logs (player, message) VALUES (?, ?)";
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getName());
                stmt.setString(2, message);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        databaseManager.closeConnection();
    }
}