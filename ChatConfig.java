package zorahm.zochat;

import org.bukkit.configuration.file.FileConfiguration;

public class ChatConfig {
    private final ChatPlugin plugin;
    private FileConfiguration config;

    public ChatConfig(ChatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    // Перезагрузка конфигурации
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // Основной формат чата (используется, если не указан локальный или глобальный чат)
    public String getChatFormat() {
        return config.getString("chat-format", "<#d45079>SW</#d45079> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}");
    }

    // Таймер антиспама в секундах
    public int getSpamCooldown() {
        return config.getInt("spam-cooldown", 3);
    }

    // Проверка, является ли слово запрещённым
    public boolean isBannedWord(String word) {
        return config.getStringList("banned-words").contains(word.toLowerCase());
    }

    // Настройки упоминаний через @
    public String getMentionFormat() {
        return config.getString("mention.format", "<yellow><bold>@{player}</bold></yellow>");
    }

    public String getMentionMessage() {
        return config.getString("mention.message", "<yellow>Тебя упомянули в чате!</yellow>");
    }

    public String getMentionSound() {
        return config.getString("mention.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
    }

    // Настройки приватных сообщений
    public String getPrivateMessageFormat() {
        return config.getString("private-messages.format", "<gradient:#f6a0d3:#b47ee5>✉ ЛС от {player}:</gradient> <white>{message}</white>");
    }

    public String getPrivateMessageReplyFormat() {
        return config.getString("private-messages.reply-format", "<gradient:#b47ee5:#f6a0d3>✉ Вы → {player}:</gradient> <white>{message}</white>");
    }

    // Настройки локального чата
    public boolean isLocalChatEnabled() {
        return config.getBoolean("local-chat.enabled", true);
    }

    public int getLocalChatRadius() {
        return config.getInt("local-chat.radius", 50);
    }

    public String getLocalChatFormat() {
        return config.getString("local-chat.format", "<#d45079>SW</#d45079> <gradient:#55ff55:#aaffaa>[Локальный]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}");
    }

    public String getLocalChatCommand() {
        return config.getString("local-chat.command", "/l");
    }

    // Настройки глобального чата
    public boolean isGlobalChatEnabled() {
        return config.getBoolean("global-chat.enabled", true);
    }

    public String getGlobalChatFormat() {
        return config.getString("global-chat.format", "<#d45079>SW</#d45079> <gradient:#ffaa33:#ffd700>[Глобальный]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}");
    }

    public String getGlobalChatCommand() {
        return config.getString("global-chat.command", "/g");
    }

    // Настройки базы данных
    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getDatabaseHost() {
        return config.getString("database.host", "localhost");
    }

    public int getDatabasePort() {
        return config.getInt("database.port", 3306);
    }

    public String getDatabaseName() {
        return config.getString("database.database", "minecraft_chat");
    }

    public String getDatabaseUsername() {
        return config.getString("database.username", "root");
    }

    public String getDatabasePassword() {
        return config.getString("database.password", "password");
    }
}