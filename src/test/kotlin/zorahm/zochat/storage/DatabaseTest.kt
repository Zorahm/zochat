package zorahm.zochat.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zorahm.zochat.Fakes
import java.io.File

class DatabaseTest {

    @Test
    fun mysqlIdColumnUsesMysqlSyntax() {
        val ddl = Database.idColumn(true)
        assertTrue(ddl.contains("AUTO_INCREMENT"), ddl)
        assertFalse(ddl.contains("AUTOINCREMENT"), "SQLite-only keyword breaks CREATE TABLE on MySQL: $ddl")
    }

    @Test
    fun sqliteIdColumnKeepsSqliteSyntax() {
        assertEquals("id INTEGER PRIMARY KEY AUTOINCREMENT", Database.idColumn(false))
    }

    @Test
    fun mysqlUrlAllowsPublicKeyRetrieval() {
        val url = Database.mysqlUrl("db.local", 3306, "chat")
        assertTrue(url.startsWith("jdbc:mysql://db.local:3306/chat?"), url)
        assertTrue(url.contains("allowPublicKeyRetrieval=true"), url)
        assertFalse(url.contains("autoReconnect"), url)
    }

    // Constructing doesn't connect, so the MySQL dialect can be checked without a server.
    private val mysql = Database.mysql(Fakes.plugin(File(".")), "db.local", 3306, "chat", "u", "p")
    private val sqlite = Database.sqlite(Fakes.plugin(File(".")))

    @Test
    fun mysqlDeclaresIndexInlineAndUsesIndexableUuidColumn() {
        assertEquals("VARCHAR(36)", mysql.uuidColumnType())
        assertEquals(", INDEX idx_x (player_uuid)", mysql.inlineIndex("idx_x", "player_uuid"))
        assertEquals("timestamp < NOW() - INTERVAL 30 DAY", mysql.olderThanDays("timestamp", 30))
    }

    @Test
    fun sqliteUsesSeparateIndexStatementAndUtcClock() {
        assertEquals("TEXT", sqlite.uuidColumnType())
        assertEquals("", sqlite.inlineIndex("idx_x", "player_uuid"))
        assertEquals("timestamp < datetime('now', '-30 days')", sqlite.olderThanDays("timestamp", 30))
    }
}
