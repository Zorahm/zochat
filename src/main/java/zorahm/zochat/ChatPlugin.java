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
    private MsgCommand msgCommand;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final HashMap<UUID, Long> lastMessageTime = new HashMap<>();

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
        getLogger().log(Level.INFO, "PlaceholderManager initialized");

        // Регистрация команды "chat"
        PluginCommand chatCommand = getCommand("chat");
        if (chatCommand != null) {
            chatCommand.setExecutor(new ChatCommand(this, chatConfig, messageManager));
        } else {
            getLogger().warning("Команда 'chat' не найдена в plugin.yml!");
        }

        // Регистрация команд /g и /l
        PluginCommand globalCommand = getCommand("global");
        if (globalCommand != null) {
            globalCommand.setExecutor(new GlobalChatCommand(this, chatConfig, messageManager));
        } else {
            getLogger().warning("Команда 'global' не найдена в plugin.yml!");
        }

        PluginCommand localCommand = getCommand("local");
        if (localCommand != null) {
            localCommand.setExecutor(new LocalChatCommand(this, chatConfig, messageManager));
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

        // Регистрация слушателей
        Bukkit.getPluginManager().registerEvents(new ChatListener(this, chatConfig, chatLogger, messageManager, lastMessageTime, placeholderManager), this);
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

    public ChatLogger getChatLogger() {
        return chatLogger;
    }

    public HashMap<UUID, Long> getLastMessageTime() {
        return lastMessageTime;
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