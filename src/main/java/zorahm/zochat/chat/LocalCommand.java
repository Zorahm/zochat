package zorahm.zochat.chat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.config.Messages;

public final class LocalCommand implements CommandExecutor {
    private final ChatService chat;
    private final Messages messages;

    public LocalCommand(ChatService chat, Messages messages) {
        this.chat = chat;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(messages.component("errors.only-players"));
            return true;
        }
        // false -> Bukkit prints the plugin.yml usage line instead of silently doing nothing.
        if (args.length == 0) return false;
        chat.send(p, String.join(" ", args), ChatChannel.LOCAL);
        return true;
    }
}
