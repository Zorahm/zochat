package zorahm.zochat.storage

import java.sql.SQLException
import java.util.UUID

class OfflineMessageRepository(private val db: Database) {

    data class OfflineMessage(
        val sender: UUID,
        val message: String,
        val sentAtMillis: Long
    )

    fun createTable() {
        // Per dialect for the same reasons as chat_logs: AUTOINCREMENT is SQLite-only and MySQL
        // can't index TEXT without a prefix length.
        val ddl = if (db.isMysql) {
            listOf(
                "CREATE TABLE IF NOT EXISTS offline_messages (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "sender_uuid VARCHAR(36) NOT NULL," +
                    "receiver_uuid VARCHAR(36) NOT NULL," +
                    "message TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX idx_offline_receiver (receiver_uuid)" +
                    ") DEFAULT CHARSET=utf8mb4"
            )
        } else {
            listOf(
                "CREATE TABLE IF NOT EXISTS offline_messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "sender_uuid TEXT NOT NULL," +
                    "receiver_uuid TEXT NOT NULL," +
                    "message TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)",
                "CREATE INDEX IF NOT EXISTS idx_offline_receiver ON offline_messages (receiver_uuid)"
            )
        }
        db.runAsync {
            try {
                db.conn().createStatement().use { st -> ddl.forEach { st.execute(it) } }
            } catch (e: SQLException) {
                db.logger.warning("create offline_messages failed: ${e.message}")
            }
        }
    }

    fun save(sender: UUID, receiver: UUID, message: String) {
        restore(receiver, listOf(OfflineMessage(sender, message, System.currentTimeMillis())))
    }

    /** Puts messages (back) in the queue, keeping their original send time. */
    fun restore(receiver: UUID, messages: List<OfflineMessage>) {
        if (messages.isEmpty()) return
        db.runAsync {
            val sql = "INSERT INTO offline_messages (sender_uuid, receiver_uuid, message, timestamp) VALUES (?, ?, ?, ?)"
            try {
                db.conn().prepareStatement(sql).use { st ->
                    for (om in messages) {
                        st.setString(1, om.sender.toString())
                        st.setString(2, receiver.toString())
                        st.setString(3, om.message)
                        db.bindTime(st, 4, om.sentAtMillis)
                        st.addBatch()
                    }
                    st.executeBatch()
                }
            } catch (e: SQLException) {
                db.logger.warning("offline insert failed: ${e.message}")
            }
        }
    }

    fun drainFor(receiver: UUID, callback: (List<OfflineMessage>) -> Unit) {
        db.runAsync {
            val out = ArrayList<OfflineMessage>()
            // Select and delete in one transaction so a failed delete can't hand the messages to the
            // player and also leave them in the table — that would re-deliver them on the next join.
            var prevAutoCommit = true
            try {
                val c = db.conn()
                prevAutoCommit = c.autoCommit
                c.autoCommit = false

                val select = "SELECT sender_uuid, message, timestamp FROM offline_messages WHERE receiver_uuid = ? ORDER BY id"
                c.prepareStatement(select).use { st ->
                    st.setString(1, receiver.toString())
                    st.executeQuery().use { rs ->
                        while (rs.next()) {
                            out.add(
                                OfflineMessage(
                                    UUID.fromString(rs.getString("sender_uuid")),
                                    rs.getString("message"),
                                    db.readTime(rs, "timestamp")
                                )
                            )
                        }
                    }
                }
                if (out.isNotEmpty()) {
                    c.prepareStatement("DELETE FROM offline_messages WHERE receiver_uuid = ?").use { del ->
                        del.setString(1, receiver.toString())
                        del.executeUpdate()
                    }
                }
                c.commit()
            } catch (e: SQLException) {
                db.logger.warning("offline drain failed: ${e.message}")
                // Don't deliver what we couldn't durably remove; the rows stay for the next join.
                out.clear()
                try {
                    db.conn().rollback()
                } catch (_: SQLException) {
                }
            } finally {
                try {
                    db.conn().autoCommit = prevAutoCommit
                } catch (_: SQLException) {
                }
            }
            callback(out)
        }
    }
}
