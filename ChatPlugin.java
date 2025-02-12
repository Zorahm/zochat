package zorahm.zochat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import zorahm.zochat.commands.ChatCommand;
import zorahm.zochat.commands.MsgCommand;
import zorahm.zochat.commands.ReplyCommand;
import zorahm.zochat.database.ChatLogger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.logging.Logger;

public final class ChatPlugin extends JavaPlugin {
    private final Logger consoleLogger = Logger.getLogger("Minecraft");

    private ChatConfig chatConfig;
    private ChatLogger chatLogger;

    @Override
    public void onEnable() {
        // Adventure API для логов
        sendConsole(Component.text("").color(NamedTextColor.GRAY));
        sendConsole(Component.text(""));
        sendConsole(
                Component.text(" ███████╗ ██████╗  ██████╗██╗  ██╗ █████╗ ████████╗")
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
        chatLogger = new ChatLogger(this); // Инициализация chatLogger

        // Получаем команду "chat" из plugin.yml
        PluginCommand chatCommand = getCommand("chat");
        if (chatCommand != null) {
            chatCommand.setExecutor(new ChatCommand(this, chatConfig, chatLogger));
        } else {
            getLogger().warning("Команда 'chat' не найдена в plugin.yml!");
        }

        // Регистрируем обработчик чата
        Bukkit.getPluginManager().registerEvents(new ChatListener(this, chatConfig, chatLogger), this);

        // Регистрируем команды msg и reply
        MsgCommand msgCommand = new MsgCommand(this);
        PluginCommand msg = getCommand("msg");
        if (msg != null) {
            msg.setExecutor(msgCommand);
        }

        PluginCommand reply = getCommand("reply");
        if (reply != null) {
            reply.setExecutor(new ReplyCommand(msgCommand));
        }

        // Регистрация TabCompleter для упоминаний
        getCommand("chat").setTabCompleter(new MentionTabCompleter());

    }

    @Override
    public void onDisable() {
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
