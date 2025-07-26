package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatConfig;
import zorahm.zochat.MessageManager;

import java.util.HashMap;
import java.util.UUID;

public class ReplyCommand implements CommandExecutor {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final MsgCommand msgCommand;
    private final MessageManager messageManager;
    private final ChatConfig chatConfig;

    public ReplyCommand(MsgCommand msgCommand, MessageManager messageManager, ChatConfig chatConfig) {
        this.msgCommand = msgCommand;
        this.messageManager = messageManager;
        this.chatConfig = chatConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("errors.only-players")));
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(miniMessage.deserialize(messageManager.getMessage("reply.usage")));
            return true;
        }

        HashMap<UUID, UUID> lastMessages = msgCommand.getLastMessages();
        UUID targetUUID = lastMessages.get(player.getUniqueId());

        if (targetUUID == null) {
            player.sendMessage(miniMessage.deserialize(messageManager.getMessage("reply.no-target")));
            return true;
        }

        Player target = player.getServer().getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            player.sendMessage(miniMessage.deserialize(messageManager.getMessage("reply.target-offline")));
            return true;
        }

        String message = String.join(" ", args);

        String incomingFormat = chatConfig.getPrivateMessageFormat();
        String outgoingFormat = chatConfig.getPrivateMessageReplyFormat();

        Component incomingMessage = miniMessage.deserialize(
                incomingFormat.replace("{player}", player.getName()).replace("{message}", message)
        );

        Component outgoingMessage = miniMessage.deserialize(
                outgoingFormat.replace("{player}", target.getName()).replace("{message}", message)
        );

        target.sendMessage(incomingMessage);
        player.sendMessage(outgoingMessage);

        return true;
    }
}