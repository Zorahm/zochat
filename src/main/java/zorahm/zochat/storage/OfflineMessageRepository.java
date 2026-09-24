package zorahm.zochat.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class OfflineMessageRepository {
    private final Database db;

    public OfflineMessageRepository(Database db) {
        this.db = db;
    }

    public record OfflineMessage(UUID sender, String message, Timestamp timestamp) {
    }

    public void createTable() {
        db.runAsync(() -> {
            try (Statement st = db.conn().createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS offline_messages (" +
                        db.idColumn() + "," +
                        "sender_uuid TEXT NOT NULL," +
                        "receiver_uuid TEXT NOT NULL," +
                        "message TEXT NOT NULL," +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
            } catch (SQLException e) {
                db.logger().warning("create offline_messages failed: " + e.getMessage());
            }
        });
    }

    public void save(UUID sender, UUID receiver, String message) {
        db.runAsync(() -> {
            String sql = "INSERT INTO offline_messages (sender_uuid, receiver_uuid, message) VALUES (?, ?, ?)";
            try (PreparedStatement st = db.conn().prepareStatement(sql)) {
                st.setString(1, sender.toString());
                st.setString(2, receiver.toString());
                st.setString(3, message);
                st.executeUpdate();
            } catch (SQLException e) {
                db.logger().warning("offline insert failed: " + e.getMessage());
            }
        });
    }

    public void drainFor(UUID receiver, Consumer<List<OfflineMessage>> callback) {
        db.runAsync(() -> {
            List<OfflineMessage> out = new ArrayList<>();
            // Select and delete in one transaction so a failed delete can't hand the messages to the
            // player and also leave them in the table — that would re-deliver them on the next join.
            Connection c;
            boolean prevAutoCommit = true;
            try {
                c = db.conn();
                prevAutoCommit = c.getAutoCommit();
                c.setAutoCommit(false);

                String select = "SELECT sender_uuid, message, timestamp FROM offline_messages WHERE receiver_uuid = ?";
                try (PreparedStatement st = c.prepareStatement(select)) {
                    st.setString(1, receiver.toString());
                    try (ResultSet rs = st.executeQuery()) {
                        while (rs.next()) {
                            out.add(new OfflineMessage(
                                    UUID.fromString(rs.getString("sender_uuid")),
                                    rs.getString("message"),
                                    rs.getTimestamp("timestamp")));
                        }
                    }
                }
                if (!out.isEmpty()) {
                    try (PreparedStatement del = c.prepareStatement(
                            "DELETE FROM offline_messages WHERE receiver_uuid = ?")) {
                        del.setString(1, receiver.toString());
                        del.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                db.logger().warning("offline drain failed: " + e.getMessage());
                // Don't deliver what we couldn't durably remove; the rows stay for the next join.
                out.clear();
                try {
                    db.conn().rollback();
                } catch (SQLException ignored) {
                }
            } finally {
                try {
                    db.conn().setAutoCommit(prevAutoCommit);
                } catch (SQLException ignored) {
                }
            }
            callback.accept(out);
        });
    }
}
