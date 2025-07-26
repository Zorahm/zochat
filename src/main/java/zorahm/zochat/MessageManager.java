package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MessageManager {
    private final ChatPlugin plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageManager(ChatPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public List<Component> getMessages(String key) {
        if (!messagesConfig.contains(key)) {
            plugin.getLogger().warning("Ключ '" + key + "' не найден в файле локализации: " + messagesFile.getName());
            return Collections.singletonList(Component.text("Сообщения недоступны."));
        }

        return messagesConfig.getStringList(key).stream()
                .map(miniMessage::deserialize)
                .collect(Collectors.toList());
    }

    public String getMessage(String path) {
        String message = messagesConfig.getString(path);
        return message != null ? message : "<red>Сообщение не найдено.</red>";
    }

    public void reloadMessages() {
        loadMessages();
        plugin.getLogger().info("Файл локализации " + messagesFile.getName() + " был успешно перезагружен.");
    }

    private void loadMessages() {
        String language = plugin.getConfig().getString("message", "en");
        File messagesFolder = new File(plugin.getDataFolder(), "messages");
        if (!messagesFolder.exists()) {
            messagesFolder.mkdirs();
        }

        messagesFile = new File(messagesFolder, "messages_" + language + ".yml");

        if (!messagesFile.exists()) {
            plugin.saveResource("messages/messages_" + language + ".yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }
}