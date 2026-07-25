package zorahm.zochat.privatemsg;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.config.Messages;

public final class MsgCommand implements CommandExecutor {
    private final PrivateMessageService pm;
    private final Messages messages;

    public MsgCommand(PrivateMessageService pm, Messages messages) {
        this.pm = pm;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(messages.component("errors.only-players"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.component("private-messages.usage"));
            return true;
        }
        String target = args[0];
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        pm.send(p, target, message);
        return true;
    }
}
