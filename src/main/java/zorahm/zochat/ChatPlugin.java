package zorahm.zochat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import zorahm.zochat.commands.*;
import zorahm.zochat.database.ChatLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChatPlugin extends JavaPlugin implements Listener {
    private final Logger consoleLogger = Logger.getLogger("Minecraft");
    private ChatConfig chatConfig;
    private ChatLogger chatLogger;
    private MessageManager messageManager;
    private LogMessageManager logMessageManager;
    private WelcomeMessageManager welcomeMessageManager;
    private PlaceholderManager placeholderManager;
    private BannedWordsManager bannedWordsManager;
    private MsgCommand msgCommand;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final HashMap<UUID, Long> lastMessageTime = new HashMap<>();

    // Новые менеджеры для модерации и функционала
    private ModerationManager moderationManager;
    private IgnoreManager ignoreManager;
    private RateLimitManager rateLimitManager;
    private MessageHistoryManager messageHistoryManager;

    @Override
    public void onEnable() {
        // Adventure API для логов
        sendConsole(Component.text("").color(NamedTextColor.GRAY));
        sendConsole(Component.text(""));
        sendConsole(
                Component.text(" ███████╗ ██████╗  ██████╗██╗  ██║ █████╗ ████████║")
                        .color(TextColor.fromHexString("#d45079"))
                        .append(Component.text("    |    Версия: ").color(NamedTextColor.GRAY))
                        .append(Component.text(getDescription().getVersion()).color(NamedTextColor.WHITE))
        );
        sendConsole(
                Component.text(" ╚══███╔╝██╔═══██╗██╔════╝██║  ██║██╔══██╗╚══██╔══")
                        .color(TextColor.fromHexString("#d45079"))
                        .append(Component.text("     |    Автор: ").color(NamedTextColor.GRAY))
                        .append(Component.text(getDescription().getAuthors().isEmpty() ? "Unknown" : getDescription().getAuthors().get(0)).color(NamedTextColor.WHITE))
        );
        sendConsole(
                Component.text("   ███╔╝ ██║   ██║██║     ███████║███████║   ██║")
                        .color(TextColor.fromHexString("#d45079"))
                        .append(Component.text("       |    Сайт: ").color(NamedTextColor.GRAY))
                        .append(Component.text(getDescription().getWebsite() != null ? getDescription().getWebsite() : "N/A").color(NamedTextColor.WHITE))
        );
        sendConsole(
                Component.text("  ███╔╝  ██║   ██║██║     ██╔══██║██╔══██║   ██║")
                        .color(TextColor.fromHexString("#d45079"))
        );
        sendConsole(
                Component.text(" ███████╗╚██████╔╝╚██████╗██║  ██║██║  ██║   ██║")
                        .color(TextColor.fromHexString("#d45079"))
        );
        sendConsole(
                Component.text(" ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝")
                        .color(TextColor.fromHexString("#d45079"))
        );
        sendConsole(Component.text(""));
        sendConsole(Component.text("").color(NamedTextColor.GRAY));

        // Проверка зависимости LuckPerms
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("LuckPerms не найден! Префиксы и суффиксы не будут работать.");
        }

        // Инициализация конфигурации
        saveDefaultConfig();
        chatConfig = new ChatConfig(this);
        chatLogger = new ChatLogger(this);
        messageManager = new MessageManager(this);
        logMessageManager = new LogMessageManager(this);
        welcomeMessageManager = new WelcomeMessageManager(this);
        placeholderManager = new PlaceholderManager(this, chatConfig);
        bannedWordsManager = new BannedWordsManager(chatConfig);
        getLogger().log(Level.INFO, "PlaceholderManager and BannedWordsManager initialized");

        // Инициализация новых менеджеров
        moderationManager = new ModerationManager(this);
        ignoreManager = new IgnoreManager(this);
        messageHistoryManager = new MessageHistoryManager(this);
        rateLimitManager = new RateLimitManager(this, moderationManager);
        rateLimitManager.startCleanupTask();
        getLogger().log(Level.INFO, "ModerationManager, IgnoreManager, MessageHistoryManager и RateLimitManager initialized");

        // Регистрация команды "chat"
        PluginCommand chatCommand = getCommand("chat");
        if (chatCommand != null) {
            chatCommand.setExecutor(new ChatCommand(this, chatConfig, messageManager));
        } else {
            getLogger().warning("Команда 'chat' не найдена в plugin.yml!");
        }

        // Создаем MentionHandler и TabCompleter
        MentionHandler mentionHandler = new MentionHandler(this, chatConfig);
        zorahm.zochat.commands.MentionTabCompleter tabCompleter = new zorahm.zochat.commands.MentionTabCompleter(mentionHandler);

        // Регистрация команд /g и /l
        PluginCommand globalCommand = getCommand("global");
        if (globalCommand != null) {
            globalCommand.setExecutor(new GlobalChatCommand(this, chatConfig, messageManager));
            globalCommand.setTabCompleter(tabCompleter);
        } else {
            getLogger().warning("Команда 'global' не найдена в plugin.yml!");
        }

        PluginCommand localCommand = getCommand("local");
        if (localCommand != null) {
            localCommand.setExecutor(new LocalChatCommand(this, chatConfig, messageManager));
            localCommand.setTabCompleter(tabCompleter);
        } else {
            getLogger().warning("Команда 'local' не найдена в plugin.yml!");
        }

        this.msgCommand = new MsgCommand(messageManager, chatConfig, this);
        PluginCommand msg = getCommand("msg");
        if (msg != null) {
            msg.setExecutor((CommandExecutor) msgCommand);
        }

        PluginCommand replyCommand = getCommand("reply");
        if (replyCommand != null) {
            replyCommand.setExecutor(new ReplyCommand(msgCommand, messageManager, chatConfig, this));
        }

        // Регистрация команд модерации
        registerCommand("mute", new zorahm.zochat.commands.MuteCommand(this, moderationManager));
        registerCommand("unmute", new zorahm.zochat.commands.UnmuteCommand(this, moderationManager));
        registerCommand("warn", new zorahm.zochat.commands.WarnCommand(this, moderationManager));
        registerCommand("chatban", new zorahm.zochat.commands.ChatBanCommand(this, moderationManager));
        registerCommand("clearchat", new zorahm.zochat.commands.ClearChatCommand(this));

        // Регистрация команд для истории и утилит
        registerCommand("history", new zorahm.zochat.commands.HistoryCommand(this, messageHistoryManager));
        registerCommand("seen", new zorahm.zochat.commands.SeenCommand(this));
        registerCommand("broadcast", new zorahm.zochat.commands.BroadcastCommand(this));

        // Регистрация команд игнорирования
        registerCommand("ignore", new zorahm.zochat.commands.IgnoreCommand(this, ignoreManager));
        registerCommand("unignore", new zorahm.zochat.commands.UnignoreCommand(this, ignoreManager));
        registerCommand("ignorelist", new zorahm.zochat.commands.IgnoreListCommand(this, ignoreManager));

        // Регистрация слушателей
        Bukkit.getPluginManager().registerEvents(new ChatListener(this, chatConfig, chatLogger, messageManager, lastMessageTime, placeholderManager, bannedWordsManager, moderationManager, ignoreManager, rateLimitManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this, chatConfig, messageManager, chatLogger), this);
        Bukkit.getPluginManager().registerEvents(new PlayerEventListener(this, chatConfig), this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    public ChatConfig getChatConfig() {
        return chatConfig;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public LogMessageManager getLogMessageManager() {
        return logMessageManager;
    }

    public WelcomeMessageManager getWelcomeMessageManager() {
        return welcomeMessageManager;
    }

    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }

    public BannedWordsManager getBannedWordsManager() {
        return bannedWordsManager;
    }

    public ChatLogger getChatLogger() {
        return chatLogger;
    }

    public HashMap<UUID, Long> getLastMessageTime() {
        return lastMessageTime;
    }

    public ModerationManager getModerationManager() {
        return moderationManager;
    }

    public IgnoreManager getIgnoreManager() {
        return ignoreManager;
    }

    public RateLimitManager getRateLimitManager() {
        return rateLimitManager;
    }

    public MessageHistoryManager getMessageHistoryManager() {
        return messageHistoryManager;
    }

    /**
     * Вспомогательный метод для регистрации команд
     */
    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof TabCompleter) {
                command.setTabCompleter((TabCompleter) executor);
            }
        } else {
            getLogger().warning("Команда '" + name + "' не найдена в plugin.yml!");
        }
    }

    @Override
    public void onDisable() {
        chatLogger.close();
        lastMessageTime.clear();
        getLogger().info("ChatPlugin отключён.");
    }

    public void reloadChatConfig() {
        reloadConfig();
        chatConfig.reload();
        messageManager.reloadMessages();
        logMessageManager.reloadLogMessages();
        welcomeMessageManager.reloadWelcomeMessages();
        lastMessageTime.clear();
        getLogger().info(ChatColor.YELLOW + "Конфигурация плагина перезагружена.");
    }

    private void sendConsole(Component component) {
        Bukkit.getConsoleSender().sendMessage(component);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        msgCommand.clearLastMessaged(event.getPlayer().getUniqueId());
        lastMessageTime.remove(event.getPlayer().getUniqueId());
    }

    public void logDebug(Level level, String key, Object... args) {
        if (chatConfig.isDebugModeEnabled()) {
            getLogger().log(level, logMessageManager.getLogMessage(key), args);
        }
    }

    public void logStandard(Level level, String key, Object... args) {
        getLogger().log(level, logMessageManager.getLogMessage(key), args);
    }
}