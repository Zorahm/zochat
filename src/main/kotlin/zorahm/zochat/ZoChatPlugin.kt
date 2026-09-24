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

    override fun onEnable() {
        displayBanner()

        // No presence check needed: LuckPerms is a hard `depend`, so Paper won't enable zoChat without it.
        val luckPerms: LuckPerms = LuckPermsProvider.get()

        ClassPreloader.preload(file, classLoader, logger)

        saveDefaultConfig()
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
        // Re-reads the setting each run, so /chat reload applies without restarting the timer. The task only
        // enqueues the DELETE on the DB thread, so running it on the main thread costs nothing.
        Bukkit.getScheduler().runTaskTimer(
            this, Runnable { chatLog.purgeOlderThan(config.chatLogRetentionDays) }, 20L * 60, 20L * 60 * 60 * 24
        )

        val bannedWords = BannedWordsFilter(config)
        val mentions = MentionHandler(this, config)
        val papi = PapiHook()
        placeholders = PlaceholderService(this, PlaceholderConfig(this), messages, papi)
        val tabCompleter = MentionTabCompleter(mentions)

        val bubbleConfig = BubbleConfig(this)
        val bubbleService = BubbleService(this, bubbleConfig, papi).also { it.start() }
        bubble = bubbleService

        val chatService = ChatService(
            config, messages, luckPerms, bannedWords, mentions, placeholders, papi, chatLog, cooldowns, bubbleService
        )
        val pm = PrivateMessageService(this, config, messages, bannedWords, placeholders, offline, cooldowns)
        welcome = WelcomeMessages(this)
        announcer = AnnouncerService(this, AnnouncerConfig(this), papi).also { it.start() }

        val guardConfig = CommandGuardConfig(this)
        this.guardConfig = guardConfig

        Bukkit.getPluginManager().registerEvents(ChatListener(this, chatService, config), this)
        Bukkit.getPluginManager().registerEvents(
            PresenceListener(this, config, messages, welcome, offline, pm, cooldowns), this
        )
        Bukkit.getPluginManager().registerEvents(CommandGuardListener(this, guardConfig, messages), this)
        Bukkit.getPluginManager().registerEvents(SayListener(config, papi), this)

        bind("chat", ChatCommand(this, config, messages), null)
        bind("global", GlobalCommand(chatService, messages), tabCompleter)
        bind("local", LocalCommand(chatService, messages), tabCompleter)
        bind("msg", MsgCommand(pm, messages), null)
        bind("reply", ReplyCommand(pm, messages), null)
        bind("chatlog", ChatLogCommand(this, chatLog, messages), null)
        bind("bubble", BubbleCommand(bubbleService, bubbleConfig, messages), null)

        // Registering touches me.clip classes, so only do it when PAPI is actually present.
        if (papi.isAvailable) {
            ZoChatExpansion(this, config, cooldowns).register()
            logger.info("Hooked into PlaceholderAPI")
        }

        logger.info("zoChat enabled")
    }

    override fun onDisable() {
        announcer?.stop()
        bubble?.stop()
        database?.close()
        cooldowns.reset()
        logger.info("zoChat disabled")
    }

    fun reloadEverything() {
        reloadConfig()
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
            Component.text(" ███████╗ ██████╗  ██████╗██╗  ██╗ █████╗ ████████╗")
                .color(TextColor.fromHexString("#d45079"))
                .append(Component.text("    |    Version: ").color(NamedTextColor.GRAY))
                .append(Component.text(meta.version).color(NamedTextColor.WHITE))
        )
        cs.sendMessage(
            Component.text(" ╚══███╔╝██╔═══██╗██╔════╝██║  ██║██╔══██╗╚══██╔══╝")
                .color(TextColor.fromHexString("#d45079"))
                .append(Component.text("    |    Author: ").color(NamedTextColor.GRAY))
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
            Component.text(" ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝")
                .color(TextColor.fromHexString("#d45079"))
        )
        cs.sendMessage(Component.text(""))
    }
}
