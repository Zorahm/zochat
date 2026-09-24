package zorahm.zochat.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
