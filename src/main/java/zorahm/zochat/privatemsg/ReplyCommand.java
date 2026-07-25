package zorahm.zochat.privatemsg;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.config.Messages;

public final class ReplyCommand implements CommandExecutor {
    private final PrivateMessageService pm;
    private final Messages messages;

    public ReplyCommand(PrivateMessageService pm, Messages messages) {
        this.pm = pm;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(messages.component("errors.only-players"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.component("reply.usage"));
            return true;
        }
        pm.reply(p, String.join(" ", args));
        return true;
    }
}
