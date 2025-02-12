package zorahm.zochat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class BannedWordsManager {
    private final JavaPlugin plugin;
    private final File bannedWordsFile;
    private FileConfiguration bannedWordsConfig;
    private Set<String> bannedWords;

    public BannedWordsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.bannedWordsFile = new File(plugin.getDataFolder(), "banned-words.yml");
        loadBannedWords();
    }

    public void loadBannedWords() {
        if (!bannedWordsFile.exists()) {
            plugin.saveResource("banned-words.yml", false);
        }
        bannedWordsConfig = YamlConfiguration.loadConfiguration(bannedWordsFile);
        bannedWords = new HashSet<>(bannedWordsConfig.getStringList("banned-words"));
    }

    public boolean isBannedWord(String word) {
        return bannedWords.contains(word.toLowerCase());
    }

    public void addBannedWord(String word) {
        bannedWords.add(word.toLowerCase());
        bannedWordsConfig.set("banned-words", new HashSet<>(bannedWords));
        save();
    }

    private void save() {
        try {
            bannedWordsConfig.save(bannedWordsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить banned-words.yml!");
        }
    }
}
