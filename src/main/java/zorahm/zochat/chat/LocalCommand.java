package zorahm.zochat.chat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.config.ChatConfig;
import zorahm.zochat.config.Messages;

public final class LocalCommand implements CommandExecutor {
    private final ChatService chat;
    private final ChatConfig config;
    private final Messages messages;

    public LocalCommand(ChatService chat, ChatConfig config, Messages messages) {
        this.chat = chat;
        this.config = config;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(messages.component("errors.only-players"));
            return true;
        }
        if (!config.isLocalChatEnabled()) {
            p.sendMessage(messages.component("chat.local-disabled"));
            return true;
        }
        if (args.length == 0) return true;
        chat.send(p, String.join(" ", args), ChatChannel.LOCAL);
        return true;
    }
}
