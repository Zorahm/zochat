package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import zorahm.zochat.ChatConfig;
import zorahm.zochat.ChatPlugin;
import zorahm.zochat.MessageManager;

import java.util.List;

public class ChatCommand implements CommandExecutor {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final MessageManager messageManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatCommand(ChatPlugin plugin, ChatConfig chatConfig, MessageManager messageManager) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            List<Component> helpMessages = messageManager.getMessages("chat.chat-help");
            helpMessages.forEach(sender::sendMessage);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("zochat.admin")) {
                sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("errors.no-permission")));
                return true;
            }
            plugin.reloadChatConfig();
            sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.reload-success")));
            return true;
        }

        sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.unknown-subcommand")));
        return true;
    }
}