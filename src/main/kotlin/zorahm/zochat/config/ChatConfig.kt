package zorahm.zochat.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import zorahm.zochat.discord.DiscordBridge
import java.io.File
import java.io.IOException

class ChatConfig(private val plugin: JavaPlugin) {

    private lateinit var cfg: FileConfiguration

    init {
        reload()
    }

    fun reload() {
        plugin.saveDefaultConfig()
        val configFile = File(plugin.dataFolder, "config.yml")
        migrateMovedTexts(configFile)
        // The console name moved out of say: since the console also sends private messages.
        ConfigFiles.renameKeys(configFile, mapOf("say.console-name" to "console.name"))
        ConfigFiles.syncWithDefaults(plugin, "config.yml", configFile)
        plugin.reloadConfig()
        this.cfg = plugin.config
    }

    // Player-visible texts (mention notifications, chat/PM/join/quit formats) moved from config.yml
    // to messages_*.yml so they follow `message:`. syncWithDefaults is about to drop the old keys —
    // carry over any text the admin customised first. The shipped defaults are skipped: they're
    // Russian and would leak into messages_en.
    private fun migrateMovedTexts(configFile: File) {
        val current = YamlConfiguration.loadConfiguration(configFile)
        val customised = customisedMovedTexts(current)
        if (customised.isEmpty()) return

        val messagesPath = Messages.fileFor(Messages.languageOf(current.getString("message")))
        val messagesFile = File(plugin.dataFolder, messagesPath)
        ConfigFiles.saveDefaultIfAbsent(plugin, messagesPath, messagesFile)
        val messages = YamlConfiguration.loadConfiguration(messagesFile)
        for ((newKey, text) in customised) {
            messages.set(newKey, text)
        }
        try {
            messages.save(messagesFile)
            plugin.logger.info("Moved customised texts (${customised.keys.joinToString()}) from config.yml to $messagesPath")
        } catch (e: IOException) {
            plugin.logger.warning("Failed to move customised texts to $messagesPath: ${e.message}")
        }
    }

    val messageLanguage: String
        get() = cfg.getString("message", "en") ?: "en"

    val messagePrefix: String
        get() = cfg.getString("message-prefix", "<#d45079>zoChat</#d45079> <#c0c0c0>•</#c0c0c0> ")
            ?: "<#d45079>zoChat</#d45079> <#c0c0c0>•</#c0c0c0> "

    val isAntiSpamEnabled: Boolean
        get() = cfg.getBoolean("anti-spam.enabled", false)

    val localChatCooldown: Int
        get() = cfg.getInt("anti-spam.local-cooldown", 3)

    val globalChatCooldown: Int
        get() = cfg.getInt("anti-spam.global-cooldown", 5)

    val privateMessageCooldown: Int
        get() = cfg.getInt("anti-spam.private-cooldown", 2)

    val bubbleCooldown: Int
        get() = cfg.getInt("anti-spam.bubble-cooldown", 3)

    val spamBypassPermission: String
        get() = cfg.getString("anti-spam.bypass-permission", "zochat.spam.bypass")
            ?: "zochat.spam.bypass"

    val mentionFormat: String
        get() = cfg.getString("mention.format", "<yellow><bold>@{player}</bold></yellow>")
            ?: "<yellow><bold>@{player}</bold></yellow>"

    val mentionSound: String
        get() = cfg.getString("mention.sound", "ENTITY_EXPERIENCE_ORB_PICKUP")
            ?: "ENTITY_EXPERIENCE_ORB_PICKUP"

    val isLocalChatEnabled: Boolean
        get() = cfg.getBoolean("local-chat.enabled", true)

    val localChatRadius: Int
        get() = cfg.getInt("local-chat.radius", 50)

    val isGlobalChatEnabled: Boolean
        get() = cfg.getBoolean("global-chat.enabled", true)

    val isSayFormattingEnabled: Boolean
        get() = cfg.getBoolean("say.enabled", true)

    val sayFormat: String
        get() = cfg.getString(
            "say.format",
            "{prefix}<gradient:#ffaa33:#ffd700>[{player}]</gradient> <#c0c0c0>›</#c0c0c0> <white>{message}</white>"
        ) ?: "{prefix}<gradient:#ffaa33:#ffd700>[{player}]</gradient> <#c0c0c0>›</#c0c0c0> <white>{message}</white>"

    val consoleSayFormat: String
        get() = consoleSayFormat(cfg, sayFormat)

    val consolePrefix: String
        get() = cfg.getString("console.prefix", "<badge:console> ") ?: ""

    val consoleName: String
        get() = cfg.getString("console.name", "Server") ?: "Server"

    val consoleDisplay: String
        get() = consoleDisplay(consolePrefix, consoleName)

    /** Saves to config.yml right away; the getters read the same live config, so it applies at once. */
    fun setConsolePrefix(prefix: String) {
        cfg.set("console.prefix", prefix)
        plugin.saveConfig()
    }

    val isBadgesEnabled: Boolean
        get() = cfg.getBoolean("badges.enabled", false)

    // "auto" follows the plugin language, so a Russian server gets Russian badges without extra setup.
    val isBadgesRussian: Boolean
        get() = when (cfg.getString("badges.language", "auto")?.lowercase()) {
            "ru" -> true
            "en" -> false
            else -> Messages.languageOf(messageLanguage) == "ru"
        }

    val isBadgeIconsEnabled: Boolean
        get() = cfg.getBoolean("badges.icons", true)

    val isDiscordEnabled: Boolean
        get() = cfg.getBoolean("discordsrv.enabled", true)

    val discordSettings: DiscordBridge.Settings
        get() = DiscordBridge.Settings(
            globalChannel = cfg.getString("discordsrv.global-channel", "global") ?: "global",
            localChat = cfg.getBoolean("discordsrv.local-chat", false),
            localChannel = cfg.getString("discordsrv.local-channel", "global") ?: "global",
        )

    val isDiscordFormatEnabled: Boolean
        get() = cfg.getBoolean("discordsrv.discord-format", true)

    val discordMaxLength: Int
        get() = cfg.getInt("discordsrv.max-length", 256).coerceAtLeast(1)

    val databaseType: String
        get() = cfg.getString("database.type", "sqlite") ?: "sqlite"

    val databaseHost: String
        get() = cfg.getString("database.mysql.host", "localhost") ?: "localhost"

    val databasePort: Int
        get() = cfg.getInt("database.mysql.port", 3306)

    val databaseName: String
        get() = cfg.getString("database.mysql.database", "minecraft_chat") ?: "minecraft_chat"

    val databaseUsername: String
        get() = cfg.getString("database.mysql.username", "root") ?: "root"

    val databasePassword: String
        get() = cfg.getString("database.mysql.password", "password") ?: "password"

    val bannedWords: List<String>
        get() = cfg.getStringList("banned-words.words")

    val isBannedWordsEnabled: Boolean
        get() = cfg.getBoolean("banned-words.enabled", true)

    val bannedWordsMode: String
        get() = cfg.getString("banned-words.mode", "smart") ?: "smart"

    val bannedWordsAction: String
        get() = cfg.getString("banned-words.action", "block") ?: "block"

    val isBannedWordsNormalizeEnabled: Boolean
        get() = cfg.getBoolean("banned-words.normalize", true)

    val isOfflineMessagesEnabled: Boolean
        get() = cfg.getBoolean("offline-messages.enabled", true)

    val isDebugModeEnabled: Boolean
        get() = cfg.getBoolean("debug-mode", false)

    val isJoinMessageEnabled: Boolean
        get() = cfg.getBoolean("join-message.enabled", true)

    val joinMessageSound: String
        get() = cfg.getString("join-message.sound", "ENTITY_PLAYER_LEVELUP") ?: "ENTITY_PLAYER_LEVELUP"

    val joinStealthPermission: String
        get() = cfg.getString("join-message.stealth-permission", "zochat.stealth.join") ?: "zochat.stealth.join"

    val isQuitMessageEnabled: Boolean
        get() = cfg.getBoolean("quit-message.enabled", true)

    val quitMessageSound: String
        get() = cfg.getString("quit-message.sound", "ENTITY_VILLAGER_NO") ?: "ENTITY_VILLAGER_NO"

    val quitStealthPermission: String
        get() = cfg.getString("quit-message.stealth-permission", "zochat.stealth.quit") ?: "zochat.stealth.quit"

    val isPlaceholderApiEnabled: Boolean
        get() = cfg.getBoolean("placeholder-api.enabled", true)

    val isPlaceholderApiFormatEnabled: Boolean
        get() = cfg.getBoolean("placeholder-api.format", true)

    val isPlaceholderApiPlayerMessagesEnabled: Boolean
        get() = cfg.getBoolean("placeholder-api.player-messages", false)

    val placeholderApiPlayerPermission: String
        get() = cfg.getString("placeholder-api.player-permission", "zochat.placeholder.papi")
            ?: "zochat.placeholder.papi"

    val mentionEveryonePermission: String
        get() = cfg.getString("mention.everyone-permission", "zochat.mention.everyone")
            ?: "zochat.mention.everyone"

    val mentionEveryoneFormat: String
        get() = cfg.getString("mention.everyone-format", "<red><bold>@everyone</bold></red>")
            ?: "<red><bold>@everyone</bold></red>"

    val mentionHerePermission: String
        get() = cfg.getString("mention.here-permission", "zochat.mention.here") ?: "zochat.mention.here"

    val mentionHereFormat: String
        get() = cfg.getString("mention.here-format", "<gold><bold>@here</bold></gold>")
            ?: "<gold><bold>@here</bold></gold>"

    val mentionHereRadius: Int
        get() = cfg.getInt("mention.here-radius", 100)

    private class MovedText(val newKey: String, val oldDefault: String)

    companion object {
        /** How the console is named in private messages; trimmed so a blank name leaves no dangling space. */
        @JvmStatic
        fun consoleDisplay(prefix: String, name: String): String = (prefix + name).trimEnd()

        /** `say.console-format`, or [sayFormat] while it is blank, so an upgrade keeps the old look. */
        @JvmStatic
        fun consoleSayFormat(config: ConfigurationSection, sayFormat: String): String =
            config.getString("say.console-format").orEmpty().ifBlank { sayFormat }

        /** New messages key -> text, for every moved config.yml key the admin changed from its old default. */
        @JvmStatic
        fun customisedMovedTexts(config: ConfigurationSection): Map<String, String> =
            MOVED_TEXTS.mapNotNull { (oldKey, moved) ->
                val text = config.getString(oldKey)
                if (config.isString(oldKey) && text != null && text != moved.oldDefault) moved.newKey to text else null
            }.toMap()

        private val MOVED_TEXTS = mapOf(
            "mention.message" to MovedText("mention.notify", "<yellow>Тебя упомянули в чате!</yellow>"),
            "mention.everyone-message" to MovedText("mention.notify-everyone", "<red>Вас упомянули через @everyone!</red>"),
            "mention.here-message" to MovedText("mention.notify-here", "<gold>Вас упомянули через @here!</gold>"),
            "local-chat.format" to MovedText(
                "formats.local-chat",
                "<gradient:#55ff55:#aaffaa>[Локальный]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}"
            ),
            "global-chat.format" to MovedText(
                "formats.global-chat",
                "<gradient:#ffaa33:#ffd700>[Глобальный]</gradient> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{prefix}{suffix}{player}<#c0c0c0> › </#c0c0c0>{message}"
            ),
            "private-messages.format" to MovedText(
                "formats.private-message",
                "<gradient:#f6a0d3:#b47ee5>✉️ ЛС от {player}:</gradient> <white>{message}</white>"
            ),
            "private-messages.reply-format" to MovedText(
                "formats.private-message-reply",
                "<gradient:#b47ee5:#f6a0d3>✉️ Вы → {player}:</gradient> <white>{message}</white>"
            ),
            "join-message.format" to MovedText("formats.join", "<gradient:#55ff55:#aaffaa>{player} присоединился к игре!</gradient>"),
            "quit-message.format" to MovedText("formats.quit", "<gradient:#ff5555:#ffaaaa>{player} покинул игру!</gradient>"),
        )
    }
}
