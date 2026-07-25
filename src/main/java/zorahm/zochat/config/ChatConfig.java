package zorahm.zochat.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public class ChatConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ChatConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        ConfigFiles.syncWithDefaults(plugin, "config.yml", configFile);
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public String getMessageLanguage() {
        return config.getString("message", "en");
    }

    public String getMessagePrefix() {
        return config.getString("message-prefix", "<#d45079>zoChat</#d45079> <#c0c0c0>•</#c0c0c0> ");
    }

    public boolean isAntiSpamEnabled() {
        return config.getBoolean("anti-spam.enabled", false);
    }

    public int getLocalChatCooldown() {
        return config.getInt("anti-spam.local-cooldown", 3);
    }

    public int getGlobalChatCooldown() {
        return config.getInt("anti-spam.global-cooldown", 5);
    }

    public int getPrivateMessageCooldown() {
        return config.getInt("anti-spam.private-cooldown", 2);
    }

    public String getSpamBypassPermission() {
        return config.getString("anti-spam.bypass-permission", "zochat.spam.bypass");
    }

    public String getMentionFormat() {
        return config.getString("mention.format", "<yellow><bold>@{player}</bold></yellow>");
    }

    public String getMentionMessage() {
        return config.getString("mention.message", "<yellow>Тебя упомянули в чате!</yellow>");
    }

    public String getMentionSound() {
        return config.getString("mention.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
    }

    public boolean isLocalChatEnabled() {
        return config.getBoolean("local-chat.enabled", true);
    }

    public int getLocalChatRadius() {
        return config.getInt("local-chat.radius", 50);
    }

    public String getLocalChatFormat() {
        return config.getString("local-chat.format", "<gradient:#55ff55:#aaffaa>[Локальный]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}");
    }

    public boolean isGlobalChatEnabled() {
        return config.getBoolean("global-chat.enabled", true);
    }

    public String getGlobalChatFormat() {
        return config.getString("global-chat.format", "<gradient:#ffaa33:#ffd700>[Глобальный]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}");
    }

    public boolean isSayFormattingEnabled() {
        return config.getBoolean("say.enabled", true);
    }

    public String getSayFormat() {
        return config.getString("say.format", "<gradient:#ffaa33:#ffd700>[{player}]</gradient> <#c0c0c0>›</#c0c0c0> <white>{message}</white>");
    }

    public String getSayConsoleName() {
        return config.getString("say.console-name", "Server");
    }

    public String getPrivateMessageFormat() {
        return config.getString("private-messages.format", "<gradient:#f6a0d3:#b47ee5>✉️ ЛС от {player}:</gradient> <white>{message}</white>");
    }

    public String getPrivateMessageReplyFormat() {
        return config.getString("private-messages.reply-format", "<gradient:#b47ee5:#f6a0d3>✉️ Вы → {player}:</gradient> <white>{message}</white>");
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getDatabaseHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getDatabasePort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getDatabaseName() {
        return config.getString("database.mysql.database", "minecraft_chat");
    }

    public String getDatabaseUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getDatabasePassword() {
        return config.getString("database.mysql.password", "password");
    }

    public List<String> getBannedWords() {
        return config.getStringList("banned-words.words");
    }

    public boolean isBannedWordsEnabled() {
        return config.getBoolean("banned-words.enabled", true);
    }

    public String getBannedWordsMode() {
        return config.getString("banned-words.mode", "smart");
    }

    public String getBannedWordsAction() {
        return config.getString("banned-words.action", "block");
    }

    public boolean isBannedWordsNormalizeEnabled() {
        return config.getBoolean("banned-words.normalize", true);
    }

    public boolean isOfflineMessagesEnabled() {
        return config.getBoolean("offline-messages.enabled", true);
    }

    public boolean isDebugModeEnabled() {
        return config.getBoolean("debug-mode", false);
    }

    public boolean isJoinMessageEnabled() {
        return config.getBoolean("join-message.enabled", true);
    }

    public String getJoinMessageFormat() {
        return config.getString("join-message.format", "<gradient:#55ff55:#aaffaa>{player} присоединился к игре!</gradient>");
    }

    public String getJoinMessageSound() {
        return config.getString("join-message.sound", "ENTITY_PLAYER_LEVELUP");
    }

    public String getJoinStealthPermission() {
        return config.getString("join-message.stealth-permission", "zochat.stealth.join");
    }

    public boolean isQuitMessageEnabled() {
        return config.getBoolean("quit-message.enabled", true);
    }

    public String getQuitMessageFormat() {
        return config.getString("quit-message.format", "<gradient:#ff5555:#ffaaaa>{player} покинул игру!</gradient>");
    }

    public String getQuitMessageSound() {
        return config.getString("quit-message.sound", "ENTITY_VILLAGER_NO");
    }

    public String getQuitStealthPermission() {
        return config.getString("quit-message.stealth-permission", "zochat.stealth.quit");
    }

    public boolean isPlaceholderApiEnabled() {
        return config.getBoolean("placeholder-api.enabled", true);
    }

    public boolean isPlaceholderApiFormatEnabled() {
        return config.getBoolean("placeholder-api.format", true);
    }

    public boolean isPlaceholderApiPlayerMessagesEnabled() {
        return config.getBoolean("placeholder-api.player-messages", false);
    }

    public String getPlaceholderApiPlayerPermission() {
        return config.getString("placeholder-api.player-permission", "zochat.placeholder.papi");
    }

    public String getMentionEveryonePermission() {
        return config.getString("mention.everyone-permission", "zochat.mention.everyone");
    }

    public String getMentionEveryoneFormat() {
        return config.getString("mention.everyone-format", "<red><bold>@everyone</bold></red>");
    }

    public String getMentionEveryoneMessage() {
        return config.getString("mention.everyone-message", "<red>Вас упомянули через @everyone!</red>");
    }

    public String getMentionHerePermission() {
        return config.getString("mention.here-permission", "zochat.mention.here");
    }

    public String getMentionHereFormat() {
        return config.getString("mention.here-format", "<gold><bold>@here</bold></gold>");
    }

    public String getMentionHereMessage() {
        return config.getString("mention.here-message", "<gold>Вас упомянули через @here!</gold>");
    }

    public int getMentionHereRadius() {
        return config.getInt("mention.here-radius", 100);
    }
}
