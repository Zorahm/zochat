package zorahm.zochat.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import zorahm.zochat.ChatConfig;
import zorahm.zochat.ChatPlugin;
import zorahm.zochat.MessageUtil;
import zorahm.zochat.database.ChatLogger;

public class ChatCommand implements CommandExecutor {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig; // Добавлено поле для chatConfig
    private final ChatLogger chatLogger; // Добавлено поле для chatLogger

    public ChatCommand(ChatPlugin plugin, ChatConfig chatConfig, ChatLogger chatLogger) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.chatLogger = chatLogger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MessageUtil.styledMessage("<red>Используйте /chat help для списка доступных команд.</red>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadChatConfig();
                sender.sendMessage(MessageUtil.styledMessage("<green>Конфигурация чата успешно перезагружена.</green>"));
                break;

            case "clear":
                for (int i = 0; i < 100; i++) {
                    sender.getServer().broadcast(MessageUtil.plainMessage(""));
                }
                sender.getServer().broadcast(MessageUtil.styledMessage("<green>Чат был очищен администратором.</green>"));
                break;

            case "help":
                sender.sendMessage(MessageUtil.styledMessage("<gold>Список команд:</gold>"));
                sender.sendMessage(MessageUtil.plainMessage("<gray>/chat reload</gray> - Перезагрузить конфигурацию чата."));
                sender.sendMessage(MessageUtil.plainMessage("<gray>/chat clear</gray> - Очистить чат."));
                sender.sendMessage(MessageUtil.plainMessage("<gray>/chat help</gray> - Показать это сообщение."));
                break;

            default:
                sender.sendMessage(MessageUtil.styledMessage("<red>Неизвестная команда. Используйте /chat help.</red>"));
                break;
        }

        return true;
    }
}
