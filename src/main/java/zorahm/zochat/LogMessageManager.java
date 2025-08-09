package zorahm.zochat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Level;

public class LogMessageManager {
    private final ChatPlugin plugin;
    private FileConfiguration logMessagesConfig;
    private File logMessagesFile;
    private final String LOG_MESSAGES_VERSION = "1.0.0";

    public LogMessageManager(ChatPlugin plugin) {
        this.plugin = plugin;
        loadLogMessages();
    }

    public String getLogMessage(String key) {
        if (logMessagesConfig == null) {
            plugin.getLogger().severe("Файл локализации логов не инициализирован!");
            return "Log message not found: " + key;
        }

        String message = logMessagesConfig.getString(key);
        if (message == null) {
            plugin.getLogger().warning("Ключ лога '" + key + "' не найден в файле: " + logMessagesFile.getName());
            return "Log message not found: " + key;
        }
        return message;
    }

    public void reloadLogMessages() {
        loadLogMessages();
        plugin.getLogger().info("Файл локализации логов " + logMessagesFile.getName() + " перезагружен.");
    }

    private void loadLogMessages() {
        String language = plugin.getConfig().getString("message", "en").toLowerCase();
        File logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
            plugin.getLogger().info("Создана папка для локализации логов: " + logsFolder.getPath());
        }

        logMessagesFile = new File(logsFolder, "log_messages_" + language + ".yml");

        if (!logMessagesFile.exists()) {
            try {
                plugin.saveResource("logs/log_messages_" + language + ".yml", false);
                plugin.getLogger().info("Сохранён файл локализации логов по умолчанию: " + logMessagesFile.getPath());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить файл локализации логов: " + logMessagesFile.getName(), e);
                logMessagesConfig = new YamlConfiguration();
                return;
            }
        }

        try {
            logMessagesConfig = YamlConfiguration.loadConfiguration(logMessagesFile);
            String currentVersion = logMessagesConfig.getString("log-messages-version", "0.0.0");
            if (!currentVersion.equals(LOG_MESSAGES_VERSION)) {
                plugin.getLogger().warning("Обнаружена устаревшая версия " + logMessagesFile.getName() + " (" + currentVersion + "). Обновление до версии " + LOG_MESSAGES_VERSION);
                if (logMessagesFile.exists()) {
                    logMessagesFile.renameTo(new File(logsFolder, "log_messages_" + language + "_old_" + currentVersion + ".yml"));
                }
                plugin.saveResource("logs/log_messages_" + language + ".yml", true);
                logMessagesConfig = YamlConfiguration.loadConfiguration(logMessagesFile);
                plugin.getLogger().info("Файл локализации логов обновлён до версии " + LOG_MESSAGES_VERSION);
            }
            plugin.getLogger().info("Загружен файл локализации логов: " + logMessagesFile.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить файл локализации логов: " + logMessagesFile.getName(), e);
            logMessagesConfig = new YamlConfiguration();
        }
    }
}