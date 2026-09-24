package zorahm.zochat.storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class Database {
    private final Plugin plugin;
    private final boolean mysql;
    private final String url;
    private final String user;
    private final String pass;

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "zoChat-DB");
        t.setDaemon(true);
        return t;
    });

    private Connection connection;

    private Database(Plugin plugin, boolean mysql, String url, String user, String pass) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    public static Database sqlite(Plugin plugin) {
        String url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "chat.db");
        return new Database(plugin, false, url, null, null);
    }

    public static Database mysql(Plugin plugin, String host, int port, String db, String user, String pass) {
        return new Database(plugin, true, mysqlUrl(host, port, db), user, pass);
    }

    static String mysqlUrl(String host, int port, String db) {
        // No autoReconnect=true: it's discouraged by MySQL (can silently drop mid-statement state);
        // conn() below revalidates and reopens dead connections instead.
        // allowPublicKeyRetrieval: MySQL 8's default caching_sha2_password auth refuses to log in over
        // a non-SSL connection without it ("Public Key Retrieval is not allowed").
        return "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true";
    }

    // Auto-increment primary key in this database's dialect, for the repositories' CREATE TABLE.
    public String idColumn() {
        return idColumn(mysql);
    }

    // AUTOINCREMENT is SQLite-only syntax — MySQL rejects the whole CREATE TABLE with it, which left
    // both tables missing and every write failing on MySQL.
    static String idColumn(boolean mysql) {
        return mysql
                ? "id BIGINT PRIMARY KEY AUTO_INCREMENT"
                : "id INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    // TEXT can't be indexed in MySQL without a prefix length; UUIDs are fixed-width anyway.
    public String uuidColumnType() {
        return mysql ? "VARCHAR(36)" : "TEXT";
    }

    // MySQL has no CREATE INDEX IF NOT EXISTS, so a separate statement would fail on every restart —
    // there the index is declared inside CREATE TABLE instead (which SQLite doesn't accept).
    public String inlineIndex(String name, String column) {
        return mysql ? ", INDEX " + name + " (" + column + ")" : "";
    }

    public void createIndex(Statement st, String name, String table, String column) throws SQLException {
        if (!mysql) {
            st.execute("CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + column + ")");
        }
    }

    // Compares against the same clock the column default uses: SQLite's CURRENT_TIMESTAMP and
    // datetime('now') are both UTC, MySQL's CURRENT_TIMESTAMP and NOW() both use the session zone.
    public String olderThanDays(String column, int days) {
        return mysql
                ? column + " < NOW() - INTERVAL " + days + " DAY"
                : column + " < datetime('now', '-" + days + " days')";
    }

    public void init() {
        try {
            openConnection();
            plugin.getLogger().info("Database connected (" + (mysql ? "MySQL" : "SQLite") + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("Database connection failed: " + e.getMessage());
        }
    }

    private void openConnection() throws SQLException, ClassNotFoundException {
        if (mysql) {
            Class.forName("zorahm.zochat.libs.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, user, pass);
        } else {
            connection = DriverManager.getConnection(url);
        }
    }

    Connection conn() throws SQLException {
        try {
            // isClosed() is only true after close() was called; a MySQL connection killed by
            // wait_timeout or a server restart still reports open. isValid() pings it — cheap
            // enough here because conn() only ever runs on the single DB thread.
            if (connection == null || connection.isClosed() || (mysql && !connection.isValid(2))) {
                closeQuietly();
                openConnection();
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found", e);
        }
        return connection;
    }

    private void closeQuietly() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }

    java.util.logging.Logger logger() {
        return plugin.getLogger();
    }

    public void runAsync(Runnable task) {
        io.execute(task);
    }

    public void close() {
        // Close the connection as the last task on the DB thread so it waits for already queued
        // writes and doesn't close underneath them from the main thread.
        io.execute(() -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing database: " + e.getMessage());
            }
        });
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
