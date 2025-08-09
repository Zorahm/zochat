package zorahm.zochat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ChatConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private final String CONFIG_VERSION = "1.5.2";

    public ChatConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        this.config = plugin.getConfig();

        String currentVersion = config.getString("config-version", "0.0.0");
        if (!currentVersion.equals(CONFIG_VERSION)) {
            plugin.getLogger().warning("Обнаружена устаревшая версия config.yml (" + currentVersion + "). Обновление до версии " + CONFIG_VERSION);
            if (configFile.exists()) {
                configFile.renameTo(new File(plugin.getDataFolder(), "config_old_" + currentVersion + ".yml"));
            }
            plugin.saveResource("config.yml", true);
            plugin.reloadConfig();
            this.config = plugin.getConfig();
            plugin.getLogger().info("Конфигурация обновлена до версии " + CONFIG_VERSION);
        } else {
            plugin.getLogger().info("Config reloaded, config-version: " + currentVersion + ", offline-messages.enabled: " + config.getBoolean("offline-messages.enabled", true));
        }
    }

    public String getMessageLanguage() {
        return config.getString("message", "en");
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
        return config.getString("anti-spam.bypass-permission", "chat.spam.bypass");
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

    public String getLocalChatCommand() {
        return config.getString("local-chat.command", "/lo");
    }

    public boolean isGlobalChatEnabled() {
        return config.getBoolean("global-chat.enabled", true);
    }

    public String getGlobalChatFormat() {
        return config.getString("global-chat.format", "<gradient:#ffaa33:#ffd700>[Глобальный]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}");
    }

    public String getGlobalChatCommand() {
        return config.getString("global-chat.command", "/g");
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

    public boolean isBannedWord(String word) {
        return getBannedWords().stream().anyMatch(banned -> word.equalsIgnoreCase(banned));
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
        return config.getString("join-message.stealth-permission", "chat.stealth.join");
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
        return config.getString("quit-message.stealth-permission", "chat.stealth.quit");
    }

    public boolean isAdvancementMessageEnabled() {
        return config.getBoolean("advancement-message.enabled", true);
    }

    public String getAdvancementMessageFormat() {
        return config.getString("advancement-message.format", "<yellow>{player} получил достижение <green>{advancement}</green>!</yellow>");
    }

    public String getAdvancementMessageSound() {
        return config.getString("advancement-message.sound", "UI_TOAST_CHALLENGE_COMPLETE");
    }

    public boolean isPlaceholdersEnabled() {
        return config.getBoolean("placeholders.enabled", true);
    }

    public boolean isPlaceholderEnabled(String placeholder) {
        return config.getBoolean("placeholders." + placeholder + ".enabled", true);
    }

    public String getPlaceholderPermission(String placeholder) {
        return config.getString("placeholders." + placeholder + ".permission", "chat.placeholder." + placeholder);
    }

    public String getPlaceholderFormat(String placeholder) {
        return config.getString("placeholders." + placeholder + ".format", "");
    }
}