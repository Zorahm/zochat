package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import zorahm.zochat.ChatConfig;
import zorahm.zochat.ChatPlugin;
import zorahm.zochat.MessageManager;

public class ChatCommand implements CommandExecutor {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final MessageManager messageManager;

    public ChatCommand(ChatPlugin plugin, ChatConfig chatConfig, MessageManager messageManager) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Использование: /chat <reload|help>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                // Перезагрузка конфигурации
                plugin.reloadChatConfig();
                messageManager.reloadMessages();
                sender.sendMessage(Component.text("Конфигурация и сообщения перезагружены!"));
                break;

            case "help":
                // Отображение справки
                for (Component line : messageManager.getMessages("chat.chat-help")) {
                    sender.sendMessage(line);
                }
                break;

            default:
                sender.sendMessage(Component.text("Неизвестная команда. Используйте /chat help для справки."));
                break;
        }

        return true;
    }
}