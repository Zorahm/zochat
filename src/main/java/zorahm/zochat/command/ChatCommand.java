package zorahm.zochat.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import zorahm.zochat.ZoChatPlugin;
import zorahm.zochat.config.ChatConfig;
import zorahm.zochat.config.Messages;

import java.util.List;

public class ChatCommand implements CommandExecutor {
    private final ZoChatPlugin plugin;
    private final ChatConfig config;
    private final Messages messages;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ChatCommand(ZoChatPlugin plugin, ChatConfig config, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            List<Component> helpMessages = messages.getList("chat.chat-help");
            helpMessages.forEach(sender::sendMessage);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("zochat.admin")) {
                sender.sendMessage(messages.component("errors.no-permission"));
                return true;
            }
            plugin.reloadEverything();
            sender.sendMessage(mm.deserialize(config.getMessagePrefix() + messages.get("chat.reload-success")));
            return true;
        }

        sender.sendMessage(mm.deserialize(config.getMessagePrefix() + messages.get("chat.unknown-subcommand")));
        return true;
    }
}
