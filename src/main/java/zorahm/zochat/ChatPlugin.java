package zorahm.zochat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import zorahm.zochat.commands.*;
import zorahm.zochat.database.ChatLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.logging.Logger;

public final class ChatPlugin extends JavaPlugin implements Listener {
    private final Logger consoleLogger = Logger.getLogger("Minecraft");
    private ChatConfig chatConfig;
    private ChatLogger chatLogger;
    private MessageManager messageManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        // Adventure API для логов
        sendConsole(Component.text("").color(NamedTextColor.GRAY));
        sendConsole(Component.text(""));
        sendConsole(
                Component.text(" ███████╗ ██████╗  ██████╗██╗  ██║ █████╗ ████████╗")
                        .color(TextColor.fromHexString("#d45079"))
                        .append(Component.text("    |    Версия: ").color(NamedTextColor.GRAY))
                        .append(Component.text(getDescription().getVersion()).color(NamedTextColor.WHITE))
        );
        sendConsole(
                Component.text(" ╚══███╔╝██╔═══██╗██╔════╝██║  ██║██╔══██╗╚══██╔══")
                        .color(TextColor.fromHexString("#d45079"))
                        .append(Component.text("     |    Автор: ").color(NamedTextColor.GRAY))
                        .append(Component.text(getDescription().getAuthors().get(0)).color(NamedTextColor.WHITE))
        );
        sendConsole(
                Component.text("   ███╔╝ ██║   ██║██║     ███████║███████║   ██║")
                        .color(TextColor.fromHexString("#d45079"))
                        .append(Component.text("       |    Сайт: ").color(NamedTextColor.GRAY))
                        .append(Component.text(getDescription().getWebsite()).color(NamedTextColor.WHITE))
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

        // Инициализация конфигурации
        saveDefaultConfig();
        chatConfig = new ChatConfig(this);
        chatLogger = new ChatLogger(this);
        messageManager = new MessageManager(this);

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

        // Регистрируем обработчик чата
        Bukkit.getPluginManager().registerEvents(new ChatListener(this, chatConfig, chatLogger, messageManager), this);

        // Регистрируем команды msg и reply
        MsgCommand msgCommand = new MsgCommand(messageManager, chatConfig);
        PluginCommand msg = getCommand("msg");
        if (msg != null) {
            msg.setExecutor(msgCommand);
        }

        PluginCommand replyCommand = getCommand("reply");
        if (replyCommand != null) {
            replyCommand.setExecutor(new ReplyCommand(msgCommand, messageManager, chatConfig));
        }

        // Регистрируем обработчик событий входа игрока
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    public ChatConfig getChatConfig() {
        return chatConfig;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    @Override
    public void onDisable() {
        chatLogger.close();
        getLogger().info("ChatPlugin отключён.");
    }

    public void reloadChatConfig() {
        reloadConfig();
        chatConfig.reload();
        getLogger().info(ChatColor.YELLOW + "Конфигурация плагина перезагружена.");
    }

    private void sendConsole(Component component) {
        Bukkit.getConsoleSender().sendMessage(component);
    }
}