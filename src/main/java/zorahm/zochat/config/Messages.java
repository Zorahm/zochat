package zorahm.zochat.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public final class Messages {
    private final Plugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private FileConfiguration cfg;
    private String fileName;

    public Messages(Plugin plugin, String language) {
        this.plugin = plugin;
        load(language);
    }

    public void load(String language) {
        String lang = (language == null) ? "en" : language.toLowerCase();
        if (!lang.equals("ru") && !lang.equals("en")) {
            plugin.getLogger().warning("Unknown language '" + lang + "', falling back to 'en'");
            lang = "en";
        }
        this.fileName = "messages/messages_" + lang + ".yml";
        File target = new File(plugin.getDataFolder(), fileName);
        ConfigFiles.saveDefaultIfAbsent(plugin, fileName, target);
        ConfigFiles.syncWithDefaults(plugin, fileName, target);
        this.cfg = YamlConfiguration.loadConfiguration(target);
    }

    public String get(String key) {
        String v = cfg.getString(key);
        if (v == null) {
            plugin.getLogger().warning("Missing message key: " + key + " in " + fileName);
            return "<red>Missing message: " + key + "</red>";
        }
        return v;
    }

    public Component component(String key) {
        return mm.deserialize(get(key));
    }

    public List<Component> getList(String key) {
        return cfg.getStringList(key).stream().map(mm::deserialize).collect(Collectors.toList());
    }
}
