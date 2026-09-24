package zorahm.zochat.storage;

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

    public record OfflineMessage(long id, UUID sender, String message, Timestamp timestamp) {
    }

    public enum SaveResult { SAVED, INBOX_FULL, FAILED }

    public void createTable() {
        db.runAsync(() -> {
            try (Statement st = db.conn().createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS offline_messages (" +
                        db.idColumn() + "," +
                        "sender_uuid " + db.uuidColumnType() + " NOT NULL," +
                        "receiver_uuid " + db.uuidColumnType() + " NOT NULL," +
                        "message TEXT NOT NULL," +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                        db.inlineIndex("idx_offline_receiver", "receiver_uuid") + ")");
                db.createIndex(st, "idx_offline_receiver", "offline_messages", "receiver_uuid");
            } catch (SQLException e) {
                db.logger().warning("create offline_messages failed: " + e.getMessage());
            }
        });
    }

    /**
     * Stores a message unless the receiver already has {@code maxPerReceiver} waiting (0 = no limit), so
     * one player can't flood another's inbox (or the database) while they're away.
     */
    public void save(UUID sender, UUID receiver, String message, int maxPerReceiver, Consumer<SaveResult> callback) {
        db.runAsync(() -> {
            SaveResult result;
            try {
                if (maxPerReceiver > 0 && countFor(receiver) >= maxPerReceiver) {
                    result = SaveResult.INBOX_FULL;
                } else {
                    // Explicit time instead of the column default: SQLite's CURRENT_TIMESTAMP is UTC text that
                    // the driver reads back as local time, so delivery showed a clock shifted by the UTC offset.
                    String sql = "INSERT INTO offline_messages (sender_uuid, receiver_uuid, message, timestamp) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement st = db.conn().prepareStatement(sql)) {
                        st.setString(1, sender.toString());
                        st.setString(2, receiver.toString());
                        st.setString(3, message);
                        st.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                        st.executeUpdate();
                    }
                    result = SaveResult.SAVED;
                }
            } catch (SQLException e) {
                db.logger().warning("offline insert failed: " + e.getMessage());
                result = SaveResult.FAILED;
            }
            callback.accept(result);
        });
    }

    private int countFor(UUID receiver) throws SQLException {
        try (PreparedStatement st = db.conn().prepareStatement(
                "SELECT COUNT(*) FROM offline_messages WHERE receiver_uuid = ?")) {
            st.setString(1, receiver.toString());
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Reads the receiver's waiting messages WITHOUT removing them: the caller deletes them via
     * {@link #delete} only once they were actually shown. Deleting on read lost every message whenever
     * the player left before the main-thread delivery ran.
     */
    public void pendingFor(UUID receiver, Consumer<List<OfflineMessage>> callback) {
        db.runAsync(() -> {
            List<OfflineMessage> out = new ArrayList<>();
            String sql = "SELECT id, sender_uuid, message, timestamp FROM offline_messages WHERE receiver_uuid = ? ORDER BY id";
            try (PreparedStatement st = db.conn().prepareStatement(sql)) {
                st.setString(1, receiver.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.add(new OfflineMessage(
                                rs.getLong("id"),
                                UUID.fromString(rs.getString("sender_uuid")),
                                rs.getString("message"),
                                rs.getTimestamp("timestamp")));
                    }
                }
            } catch (SQLException e) {
                db.logger().warning("offline select failed: " + e.getMessage());
                out.clear();
            }
            callback.accept(out);
        });
    }

    // By id rather than by receiver, so a message that arrived after pendingFor() read is kept.
    public void delete(List<OfflineMessage> delivered) {
        if (delivered.isEmpty()) {
            return;
        }
        db.runAsync(() -> {
            try (PreparedStatement st = db.conn().prepareStatement("DELETE FROM offline_messages WHERE id = ?")) {
                for (OfflineMessage m : delivered) {
                    st.setLong(1, m.id());
                    st.addBatch();
                }
                st.executeBatch();
            } catch (SQLException e) {
                db.logger().warning("offline delete failed: " + e.getMessage());
            }
        });
    }
}
