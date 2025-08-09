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

public class MessageManager {
    private final ChatPlugin plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final String MESSAGES_VERSION = "1.0.0";

    public MessageManager(ChatPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public List<Component> getMessages(String key) {
        if (!messagesConfig.contains(key)) {
            plugin.getLogger().warning("Ключ '" + key + "' не найден в файле локализации: " + messagesFile.getName());
            return Collections.singletonList(Component.text("<red>Сообщения недоступны.</red>"));
        }

        return messagesConfig.getStringList(key).stream()
                .map(miniMessage::deserialize)
                .collect(Collectors.toList());
    }

    public String getMessage(String path) {
        if (messagesConfig == null) {
            plugin.getLogger().severe("Файл локализации не инициализирован!");
            return "<red>Ошибка: Файл локализации не загружен.</red>";
        }

        String message = messagesConfig.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Ключ '" + path + "' не найден в файле локализации: " + messagesFile.getName());
            return "<red>Сообщение не найдено: " + path + "</red>";
        }
        return message;
    }

    public void reloadMessages() {
        loadMessages();
        plugin.getLogger().info("Файл локализации " + messagesFile.getName() + " был успешно перезагружен.");
    }

    private void loadMessages() {
        String language = plugin.getConfig().getString("message", "en").toLowerCase();
        File messagesFolder = new File(plugin.getDataFolder(), "messages");
        if (!messagesFolder.exists()) {
            messagesFolder.mkdirs();
            plugin.getLogger().info("Создана папка для локализаций: " + messagesFolder.getPath());
        }

        messagesFile = new File(messagesFolder, "messages_" + language + ".yml");

        if (!messagesFile.exists()) {
            try {
                plugin.saveResource("messages/messages_" + language + ".yml", false);
                plugin.getLogger().info("Сохранён файл локализации по умолчанию: " + messagesFile.getPath());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить файл локализации: " + messagesFile.getName(), e);
                messagesConfig = new YamlConfiguration();
                return;
            }
        }

        try {
            messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
            String currentVersion = messagesConfig.getString("messages-version", "0.0.0");
            if (!currentVersion.equals(MESSAGES_VERSION)) {
                plugin.getLogger().warning("Обнаружена устаревшая версия " + messagesFile.getName() + " (" + currentVersion + "). Обновление до версии " + MESSAGES_VERSION);
                if (messagesFile.exists()) {
                    messagesFile.renameTo(new File(messagesFolder, "messages_" + language + "_old_" + currentVersion + ".yml"));
                }
                plugin.saveResource("messages/messages_" + language + ".yml", true);
                messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
                plugin.getLogger().info("Файл локализации обновлён до версии " + MESSAGES_VERSION);
            }
            plugin.getLogger().info("Загружен файл локализации: " + messagesFile.getName());
            if (!messagesConfig.contains("chat.message-timestamp")) {
                plugin.getLogger().warning("Ключ 'chat.message-timestamp' отсутствует в " + messagesFile.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить файл локализации: " + messagesFile.getName(), e);
            messagesConfig = new YamlConfiguration();
        }
    }
}