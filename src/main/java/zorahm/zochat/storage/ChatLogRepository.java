package zorahm.zochat.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class ChatLogRepository {
    private final Database db;

    public ChatLogRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.runAsync(() -> {
            try (Statement st = db.conn().createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS chat_logs (" +
                        db.idColumn() + "," +
                        "player_uuid TEXT NOT NULL," +
                        "message TEXT NOT NULL," +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
            } catch (SQLException e) {
                db.logger().warning("create chat_logs failed: " + e.getMessage());
            }
        });
    }

    public void log(UUID player, String message) {
        db.runAsync(() -> {
            String sql = "INSERT INTO chat_logs (player_uuid, message) VALUES (?, ?)";
            try (PreparedStatement st = db.conn().prepareStatement(sql)) {
                st.setString(1, player.toString());
                st.setString(2, message);
                st.executeUpdate();
            } catch (SQLException e) {
                db.logger().warning("chat_logs insert failed: " + e.getMessage());
            }
        });
    }

    public void recentFor(UUID player, Consumer<List<String>> callback) {
        db.runAsync(() -> {
            List<String> out = new ArrayList<>();
            String sql = "SELECT message FROM chat_logs WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT 10";
            try (PreparedStatement st = db.conn().prepareStatement(sql)) {
                st.setString(1, player.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString("message"));
                    }
                }
            } catch (SQLException e) {
                db.logger().warning("chat_logs select failed: " + e.getMessage());
            }
            callback.accept(out);
        });
    }

    public void clearAll(Consumer<Integer> callback) {
        db.runAsync(() -> {
            int count = 0;
            try (Statement st = db.conn().createStatement()) {
                count = st.executeUpdate("DELETE FROM chat_logs");
            } catch (SQLException e) {
                db.logger().warning("chat_logs clear failed: " + e.getMessage());
            }
            callback.accept(count);
        });
    }
}
