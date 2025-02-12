package zorahm.zochat.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        connect();
        createTables();
    }

    public void connect() {
        try {
            String type = plugin.getConfig().getString("database.type", "sqlite");
            if (type.equalsIgnoreCase("mysql")) {
                String host = plugin.getConfig().getString("database.host", "localhost");
                int port = plugin.getConfig().getInt("database.port", 3306);
                String database = plugin.getConfig().getString("database.database", "minecraft_chat");
                String username = plugin.getConfig().getString("database.username", "root");
                String password = plugin.getConfig().getString("database.password", "password");

                String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
                connection = DriverManager.getConnection(url, username, password);
            } else {
                String url = "jdbc:sqlite:" + plugin.getDataFolder() + "/chat_logs.db";
                connection = DriverManager.getConnection(url);
            }
            plugin.getLogger().info("✅ Подключено к базе данных!");
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Ошибка подключения к базе данных: " + e.getMessage());
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player VARCHAR(16), " +
                    "message TEXT, " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Ошибка создания таблиц: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }
}
