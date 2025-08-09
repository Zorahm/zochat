package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class WelcomeMessageManager {
    private final ChatPlugin plugin;
    private FileConfiguration welcomeMessagesConfig;
    private File welcomeMessagesFile;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final String WELCOME_MESSAGES_VERSION = "1.0.0";

    public WelcomeMessageManager(ChatPlugin plugin) {
        this.plugin = plugin;
        loadWelcomeMessages();
    }

    public List<Component> getWelcomeMessages(String playerName) {
        if (welcomeMessagesConfig == null) {
            plugin.getLogger().severe("Файл Welcome-сообщений не инициализирован!");
            return Collections.singletonList(Component.text("<red>Ошибка: Файл Welcome-сообщений не загружен.</red>"));
        }

        List<String> messages = welcomeMessagesConfig.getStringList("messages");
        if (messages.isEmpty()) {
            plugin.getLogger().warning("Список Welcome-сообщений пуст в файле: " + welcomeMessagesFile.getName());
            return Collections.emptyList();
        }

        return messages.stream()
                .map(message -> miniMessage.deserialize(message.replace("{player}", playerName)))
                .collect(Collectors.toList());
    }

    public boolean isWelcomeMessagesEnabled() {
        return welcomeMessagesConfig.getBoolean("enabled", true);
    }

    public long getWelcomeMessagesDelay() {
        return welcomeMessagesConfig.getLong("delay", 20L);
    }

    public void reloadWelcomeMessages() {
        loadWelcomeMessages();
        plugin.getLogger().info("Файл Welcome-сообщений " + welcomeMessagesFile.getName() + " перезагружен.");
    }

    private void loadWelcomeMessages() {
        File welcomeMessagesFolder = new File(plugin.getDataFolder(), "welcome_messages");
        if (!welcomeMessagesFolder.exists()) {
            welcomeMessagesFolder.mkdirs();
            plugin.getLogger().info("Создана папка для Welcome-сообщений: " + welcomeMessagesFolder.getPath());
        }

        welcomeMessagesFile = new File(welcomeMessagesFolder, "welcome_messages.yml");

        if (!welcomeMessagesFile.exists()) {
            try {
                plugin.saveResource("welcome_messages/welcome_messages.yml", false);
                plugin.getLogger().info("Сохранён файл Welcome-сообщений по умолчанию: " + welcomeMessagesFile.getPath());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить файл Welcome-сообщений: " + welcomeMessagesFile.getName(), e);
                welcomeMessagesConfig = new YamlConfiguration();
                return;
            }
        }

        try {
            welcomeMessagesConfig = YamlConfiguration.loadConfiguration(welcomeMessagesFile);
            String currentVersion = welcomeMessagesConfig.getString("welcome-messages-version", "0.0.0");
            if (!currentVersion.equals(WELCOME_MESSAGES_VERSION)) {
                plugin.getLogger().warning("Обнаружена устаревшая версия " + welcomeMessagesFile.getName() + " (" + currentVersion + "). Обновление до версии " + WELCOME_MESSAGES_VERSION);
                if (welcomeMessagesFile.exists()) {
                    welcomeMessagesFile.renameTo(new File(welcomeMessagesFolder, "welcome_messages_old_" + currentVersion + ".yml"));
                }
                plugin.saveResource("welcome_messages/welcome_messages.yml", true);
                welcomeMessagesConfig = YamlConfiguration.loadConfiguration(welcomeMessagesFile);
                plugin.getLogger().info("Файл Welcome-сообщений обновлён до версии " + WELCOME_MESSAGES_VERSION);
            }
            plugin.getLogger().info("Загружен файл Welcome-сообщений: " + welcomeMessagesFile.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить файл Welcome-сообщений: " + welcomeMessagesFile.getName(), e);
            welcomeMessagesConfig = new YamlConfiguration();
        }
    }
}