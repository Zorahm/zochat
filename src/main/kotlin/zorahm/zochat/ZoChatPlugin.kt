package zorahm.zochat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin
import zorahm.zochat.announcer.AnnouncerConfig
import zorahm.zochat.announcer.AnnouncerService
import zorahm.zochat.badge.BadgeTable
import zorahm.zochat.badge.Badges
import zorahm.zochat.bubble.BubbleCommand
import zorahm.zochat.bubble.BubbleConfig
import zorahm.zochat.bubble.BubbleService
import zorahm.zochat.chat.BannedWordsFilter
import zorahm.zochat.chat.ChatListener
import zorahm.zochat.chat.ChatService
import zorahm.zochat.chat.CooldownService
import zorahm.zochat.chat.GlobalCommand
import zorahm.zochat.chat.LocalCommand
import zorahm.zochat.chat.MentionHandler
import zorahm.zochat.chat.MentionTabCompleter
import zorahm.zochat.chat.PapiHook
import zorahm.zochat.chat.PlaceholderConfig
import zorahm.zochat.chat.PlaceholderService
import zorahm.zochat.chat.ZoChatExpansion
import zorahm.zochat.command.ChatCommand
import zorahm.zochat.command.ChatLogCommand
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages
import zorahm.zochat.discord.DiscordBridge
import zorahm.zochat.discord.DiscordSrvHook
import zorahm.zochat.guard.CommandGuardConfig
import zorahm.zochat.guard.CommandGuardListener
import zorahm.zochat.presence.PresenceListener
import zorahm.zochat.presence.WelcomeMessages
import zorahm.zochat.say.SayListener
import zorahm.zochat.privatemsg.MsgCommand
import zorahm.zochat.privatemsg.PrivateMessageService
import zorahm.zochat.privatemsg.ReplyCommand
import zorahm.zochat.storage.ChatLogRepository
import zorahm.zochat.storage.Database
import zorahm.zochat.storage.OfflineMessageRepository
import zorahm.zochat.util.ClassPreloader

class ZoChatPlugin : JavaPlugin() {

    private lateinit var config: ChatConfig
    private lateinit var messages: Messages
    private var database: Database? = null
    private val cooldowns = CooldownService()
    private lateinit var placeholders: PlaceholderService
    private lateinit var welcome: WelcomeMessages
    private var announcer: AnnouncerService? = null
    private var bubble: BubbleService? = null
    private var guardConfig: CommandGuardConfig? = null
    private var discord: DiscordBridge = DiscordBridge.None

    override fun onEnable() {
        displayBanner()

        // `depend` guarantees LuckPerms is installed, but it can still have failed to enable — then
        // LuckPermsProvider.get() throws. Bail out with a readable message instead of a stack trace.
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            logger.severe("LuckPerms is not enabled - disabling zoChat. Download: https://luckperms.net/download")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        val luckPerms: LuckPerms = LuckPermsProvider.get()

        ClassPreloader.preload(file, classLoader, logger)

        config = ChatConfig(this)
        messages = Messages(this, config.messageLanguage)

        val db = if (config.databaseType.equals("mysql", ignoreCase = true)) {
            Database.mysql(
                this, config.databaseHost, config.databasePort,
                config.databaseName, config.databaseUsername, config.databasePassword
            )
        } else {
            Database.sqlite(this)
        }
        database = db
        db.init()
        val chatLog = ChatLogRepository(db)
        val offline = OfflineMessageRepository(db)
        chatLog.createTable()
        offline.createTable()

        val bannedWords = BannedWordsFilter(config)
        val mentions = MentionHandler(this, config, messages)
        val papi = PapiHook()
        placeholders = PlaceholderService(this, PlaceholderConfig(this), messages, papi, bannedWords)
        val tabCompleter = MentionTabCompleter(mentions)

        val bubbleConfig = BubbleConfig(this)
        val bubbleService = BubbleService(this, bubbleConfig, papi).also { it.start() }
        bubble = bubbleService

        val badges = Badges(BadgeTable.load(this)) {
            Badges.Settings(config.isBadgesEnabled, config.isBadgesRussian, config.isBadgeIconsEnabled)
        }

        // Constructing the hook touches github.scarsz classes, so only do it when DiscordSRV is present
        // (softdepend: it's enabled before us). discordsrv.enabled is read live, so /chat reload can toggle it.
        if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            discord = DiscordSrvHook(this, config, messages, bannedWords).also { it.start() }
            logger.info("Hooked into DiscordSRV")
        }

        val chatService = ChatService(
            config, messages, luckPerms, bannedWords, mentions, placeholders, papi, chatLog, cooldowns, bubbleService, badges, discord
        )
        val pm = PrivateMessageService(config, messages, bannedWords, placeholders, offline, cooldowns, badges)
        welcome = WelcomeMessages(this)
        announcer = AnnouncerService(this, AnnouncerConfig(this), papi).also { it.start() }

        val guardConfig = CommandGuardConfig(this)
        this.guardConfig = guardConfig

        val chatListener = ChatListener(this, chatService, config, messages)
        Bukkit.getPluginManager().registerEvents(chatListener, this)
        Bukkit.getPluginManager().registerEvents(
            PresenceListener(this, config, messages, welcome, offline, pm, cooldowns), this
        )
        Bukkit.getPluginManager().registerEvents(CommandGuardListener(this, guardConfig, messages), this)
        Bukkit.getPluginManager().registerEvents(SayListener(config, papi, badges), this)

        val chatCommand = ChatCommand(this, config, messages, badges)
        bind("chat", chatCommand, chatCommand)
        bind("global", GlobalCommand(chatListener, config, messages), tabCompleter)
        bind("local", LocalCommand(chatListener, config, messages), tabCompleter)
        bind("msg", MsgCommand(pm, messages), null)
        bind("reply", ReplyCommand(pm, messages), null)
        bind("chatlog", ChatLogCommand(this, chatLog, messages), null)
        bind("bubble", BubbleCommand(chatListener, bubbleConfig, messages), null)

        // Registering touches me.clip classes, so only do it when PAPI is actually present.
        if (papi.isAvailable) {
            ZoChatExpansion(this, config, cooldowns).register()
            logger.info("Hooked into PlaceholderAPI")
        }

        logger.info("zoChat enabled")
    }

    override fun onDisable() {
        discord.close()
        announcer?.stop()
        bubble?.stop()
        database?.close()
        cooldowns.reset()
        logger.info("zoChat disabled")
    }

    fun reloadEverything() {
        config.reload()
        messages.load(config.messageLanguage)
        placeholders.reload()
        welcome.reload()
        announcer?.reload()
        bubble?.reload()
        guardConfig?.reload()
        cooldowns.reset()
        logger.info("zoChat reloaded")
    }

    private fun bind(name: String, executor: CommandExecutor, tabCompleter: TabCompleter?) {
        val command = getCommand(name)
        if (command == null) {
            logger.warning("Command '$name' missing from plugin.yml")
            return
        }
        command.setExecutor(executor)
        if (tabCompleter != null) command.tabCompleter = tabCompleter
    }

    private fun displayBanner() {
        val cs = Bukkit.getConsoleSender()
        val meta = pluginMeta
        cs.sendMessage(Component.text(""))
        cs.sendMessage(
            Component.text(" ███████╗ ██████╗  ██████╗██╗  ██║ █████╗ ████████║")
                .color(TextColor.fromHexString("#d45079"))
                .append(Component.text("    |    Version: ").color(NamedTextColor.GRAY))
                .append(Component.text(meta.version).color(NamedTextColor.WHITE))
        )
        cs.sendMessage(
            Component.text(" ╚══███╔╝██╔═══██╗██╔════╝██║  ██║██╔══██╗╚══██╔══")
                .color(TextColor.fromHexString("#d45079"))
                .append(Component.text("     |    Author: ").color(NamedTextColor.GRAY))
                .append(Component.text(meta.authors.firstOrNull() ?: "Unknown").color(NamedTextColor.WHITE))
        )
        cs.sendMessage(
            Component.text("   ███╔╝ ██║   ██║██║     ███████║███████║   ██║")
                .color(TextColor.fromHexString("#d45079"))
                .append(Component.text("       |    Website: ").color(NamedTextColor.GRAY))
                .append(Component.text(meta.website ?: "N/A").color(NamedTextColor.WHITE))
        )
        cs.sendMessage(
            Component.text("  ███╔╝  ██║   ██║██║     ██╔══██║██╔══██║   ██║")
                .color(TextColor.fromHexString("#d45079"))
        )
        cs.sendMessage(
            Component.text(" ███████╗╚██████╔╝╚██████╗██║  ██║██║  ██║   ██║")
                .color(TextColor.fromHexString("#d45079"))
        )
        cs.sendMessage(
            Component.text(" ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝   ══╝")
                .color(TextColor.fromHexString("#d45079"))
        )
        cs.sendMessage(Component.text(""))
    }
}
