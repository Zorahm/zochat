package zorahm.zochat.presence;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import zorahm.zochat.config.ConfigFiles;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WelcomeMessages {
    private final Plugin plugin;
    private FileConfiguration cfg;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public WelcomeMessages(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public List<Component> getWelcomeMessages(String playerName) {
        if (cfg == null) {
            return Collections.emptyList();
        }
        List<String> messages = cfg.getStringList("messages");
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages.stream()
                .map(message -> mm.deserialize(message.replace("{player}", playerName)))
                .collect(Collectors.toList());
    }

    public boolean isEnabled() {
        return cfg != null && cfg.getBoolean("enabled", false);
    }

    public long getDelay() {
        return cfg != null ? cfg.getLong("delay", 20L) : 20L;
    }

    public void reload() {
        load();
    }

    private void load() {
        String resourcePath = "welcome_messages/welcome_messages.yml";
        File target = new File(plugin.getDataFolder(), resourcePath);
        ConfigFiles.saveDefaultIfAbsent(plugin, resourcePath, target);
        ConfigFiles.syncWithDefaults(plugin, resourcePath, target);
        this.cfg = YamlConfiguration.loadConfiguration(target);
    }
}
