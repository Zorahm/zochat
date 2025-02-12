package zorahm.zochat;

import org.bukkit.configuration.file.FileConfiguration;

public class ChatConfig {
    private final ChatPlugin plugin;
    private FileConfiguration config;


    public ChatConfig(ChatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public String getChatFormat() {
        return config.getString("chat-format", "<gray>[<prefix>] <bold><player>:</bold> <suffix> <color:#ff5733>{message}</color>");
    }

    public int getSpamCooldown() {
        return config.getInt("spam-cooldown", 3);
    }

    public boolean isBannedWord(String word) {
        return config.getStringList("banned-words").contains(word.toLowerCase());
    }

    // Методы для работы с упоминаниями
    public String getMentionFormat() {
        return config.getString("mention.format", "<yellow><bold>@{player}</bold></yellow>");
    }

    public String getMentionMessage() {
        return config.getString("mention.message", "<yellow>Тебя упомянули в чате!</yellow>");
    }

    public String getMentionSound() {
        return config.getString("mention.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
    }
}
