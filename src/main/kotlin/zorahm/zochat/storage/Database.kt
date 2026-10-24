package zorahm.zochat.storage

import org.bukkit.plugin.Plugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class Database private constructor(
    internal val logger: Logger,
    internal val isMysql: Boolean,
    private val url: String,
    private val user: String?,
    private val pass: String?
) {

    private val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "zoChat-DB").apply { isDaemon = true }
    }

    private var connection: Connection? = null

    fun init() {
        try {
            openConnection()
            logger.info("Database connected (${if (isMysql) "MySQL" else "SQLite"})")
        } catch (e: Exception) {
            logger.severe("Database connection failed: ${e.message}")
        }
    }

    private fun openConnection(): Connection {
        val opened: Connection = if (isMysql) {
            // The MySQL driver is relocated by Shadow to zorahm.zochat.libs.* — DriverManager
            // resolves it by the relocated name, so Class.forName must use the same one.
            Class.forName("zorahm.zochat.libs.mysql.cj.jdbc.Driver")
            DriverManager.getConnection(url, user, pass)
        } else {
            DriverManager.getConnection(url)
        }
        connection = opened
        return opened
    }

    internal fun conn(): Connection {
        val current = connection
        return try {
            // isClosed() is only true after close() was called; a MySQL connection killed by
            // wait_timeout or a server restart still reports open. isValid() pings it — cheap
            // enough here because conn() only ever runs on the single DB thread.
            if (current == null || current.isClosed || (isMysql && !current.isValid(2))) {
                closeQuietly()
                openConnection()
            } else {
                current
            }
        } catch (e: ClassNotFoundException) {
            throw SQLException("JDBC driver not found", e)
        }
    }

    private fun closeQuietly() {
        val current = connection ?: return
        try {
            current.close()
        } catch (_: SQLException) {
        }
        connection = null
    }

    // SQLite's CURRENT_TIMESTAMP is UTC text, but sqlite-jdbc reads that text back through
    // getTimestamp() as LOCAL time, shifting every stored time by the host's UTC offset. SQLite
    // times are therefore written and parsed as UTC text in that same format (old rows stay
    // readable); MySQL keeps a native DATETIME round trip through the driver.
    internal fun bindTime(st: PreparedStatement, index: Int, millis: Long) {
        if (isMysql) {
            st.setTimestamp(index, Timestamp(millis))
        } else {
            st.setString(index, SQLITE_TIME.format(Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC)))
        }
    }

    internal fun readTime(rs: ResultSet, column: String): Long {
        if (isMysql) return rs.getTimestamp(column)?.time ?: System.currentTimeMillis()
        val text = rs.getString(column) ?: return System.currentTimeMillis()
        return try {
            LocalDateTime.parse(text, SQLITE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e: DateTimeParseException) {
            // A time we can't read must not block delivery of the message itself.
            System.currentTimeMillis()
        }
    }

    fun runAsync(task: () -> Unit) {
        io.execute { task() }
    }

    fun close() {
        // Close the connection as the last task on the DB thread so it waits for already queued
        // writes and doesn't close underneath them from the main thread.
        io.execute {
            try {
                val current = connection
                if (current != null && !current.isClosed) {
                    current.close()
                }
            } catch (e: SQLException) {
                logger.warning("Error closing database: ${e.message}")
            }
        }
        io.shutdown()
        try {
            io.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private val SQLITE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        @JvmStatic
        fun sqlite(plugin: Plugin): Database = sqlite(File(plugin.dataFolder, "chat.db"), plugin.logger)

        @JvmStatic
        fun sqlite(file: File, logger: Logger): Database = Database(logger, false, "jdbc:sqlite:$file", null, null)

        @JvmStatic
        fun mysql(plugin: Plugin, host: String, port: Int, db: String, user: String, pass: String): Database {
            // No autoReconnect=true: it's discouraged by MySQL (can silently drop mid-statement state);
            // conn() above revalidates and reopens dead connections instead.
            // allowPublicKeyRetrieval: without SSL, MySQL 8's default caching_sha2_password auth
            // refuses the first login with "Public Key Retrieval is not allowed".
            val url = "jdbc:mysql://$host:$port/$db?useSSL=false&allowPublicKeyRetrieval=true"
            return Database(plugin.logger, true, url, user, pass)
        }
    }
}
