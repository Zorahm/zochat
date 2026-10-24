package zorahm.zochat.storage

import java.sql.SQLException
import java.util.UUID

class ChatLogRepository(private val db: Database) {

    fun createTable() {
        // AUTOINCREMENT is SQLite-only (MySQL rejects it — the table was never created there), and
        // MySQL can't index a TEXT column without a prefix length, so the schema is per dialect.
        val ddl = if (db.isMysql) {
            listOf(
                "CREATE TABLE IF NOT EXISTS chat_logs (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "message TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX idx_chat_logs_player (player_uuid)" +
                    ") DEFAULT CHARSET=utf8mb4"
            )
        } else {
            listOf(
                "CREATE TABLE IF NOT EXISTS chat_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "message TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)",
                "CREATE INDEX IF NOT EXISTS idx_chat_logs_player ON chat_logs (player_uuid)"
            )
        }
        db.runAsync {
            try {
                db.conn().createStatement().use { st -> ddl.forEach { st.execute(it) } }
            } catch (e: SQLException) {
                db.logger.warning("create chat_logs failed: ${e.message}")
            }
        }
    }

    fun log(player: UUID, message: String) {
        val sentAt = System.currentTimeMillis()
        db.runAsync {
            val sql = "INSERT INTO chat_logs (player_uuid, message, timestamp) VALUES (?, ?, ?)"
            try {
                db.conn().prepareStatement(sql).use { st ->
                    st.setString(1, player.toString())
                    st.setString(2, message)
                    db.bindTime(st, 3, sentAt)
                    st.executeUpdate()
                }
            } catch (e: SQLException) {
                db.logger.warning("chat_logs insert failed: ${e.message}")
            }
        }
    }

    /** Newest first; the callback gets null when the query failed (vs. an empty list = no messages). */
    fun recentFor(player: UUID, callback: (List<String>?) -> Unit) {
        db.runAsync {
            val out = ArrayList<String>()
            // By id, not timestamp: timestamps have 1-second resolution, so messages sent within the
            // same second came back in arbitrary order.
            val sql = "SELECT message FROM chat_logs WHERE player_uuid = ? ORDER BY id DESC LIMIT 10"
            val result: List<String>? = try {
                db.conn().prepareStatement(sql).use { st ->
                    st.setString(1, player.toString())
                    st.executeQuery().use { rs ->
                        while (rs.next()) {
                            out.add(rs.getString("message"))
                        }
                    }
                }
                out
            } catch (e: SQLException) {
                db.logger.warning("chat_logs select failed: ${e.message}")
                null
            }
            callback(result)
        }
    }

    /** The callback gets the number of deleted rows, or null when the delete failed. */
    fun clearAll(callback: (Int?) -> Unit) {
        db.runAsync {
            val count: Int? = try {
                db.conn().createStatement().use { st -> st.executeUpdate("DELETE FROM chat_logs") }
            } catch (e: SQLException) {
                db.logger.warning("chat_logs clear failed: ${e.message}")
                null
            }
            callback(count)
        }
    }
}
