package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatConfig;
import zorahm.zochat.ChatPlugin;
import zorahm.zochat.MessageManager;

import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

public class ReplyCommand implements CommandExecutor {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final MsgCommand msgCommand;
    private final MessageManager messageManager;
    private final ChatConfig chatConfig;
    private final ChatPlugin plugin;

    public ReplyCommand(MsgCommand msgCommand, MessageManager messageManager, ChatConfig chatConfig, ChatPlugin plugin) {
        this.msgCommand = msgCommand;
        this.messageManager = messageManager;
        this.chatConfig = chatConfig;
        this.plugin = plugin;
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

        if (chatConfig.isAntiSpamEnabled() && !player.hasPermission(chatConfig.getSpamBypassPermission())) {
            long now = System.currentTimeMillis();
            long lastTime = msgCommand.getLastMessagesTime().getOrDefault(player.getUniqueId(), 0L);
            int cooldown = chatConfig.getPrivateMessageCooldown();
            plugin.getLogger().log(Level.INFO, "Checking PM cooldown for {0}: now={1}, lastTime={2}, cooldown={3}s",
                    new Object[]{player.getName(), now, lastTime, cooldown});
            if (now - lastTime < cooldown * 1000L) {
                player.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.spam-warning")));
                return true;
            }
            msgCommand.getLastMessagesTime().put(player.getUniqueId(), now);
            plugin.getLogger().log(Level.INFO, "Updated PM lastMessageTime for {0}: {1}", new Object[]{player.getName(), now});
        }

        String message = String.join(" ", args);

        String incomingFormat = chatConfig.getPrivateMessageFormat();
        String outgoingFormat = chatConfig.getPrivateMessageReplyFormat();

        Component incomingMessage = miniMessage.deserialize(
                        incomingFormat.replace("{player}", player.getName()).replace("{message}", message)
                ).clickEvent(ClickEvent.suggestCommand("/r "))
                .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы ответить")));

        Component outgoingMessage = miniMessage.deserialize(
                        outgoingFormat.replace("{player}", target.getName()).replace("{message}", message)
                ).clickEvent(ClickEvent.suggestCommand("/msg " + target.getName() + " "))
                .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы отправить ещё сообщение")));

        target.sendMessage(incomingMessage);
        player.sendMessage(outgoingMessage);

        lastMessages.put(target.getUniqueId(), player.getUniqueId());
        lastMessages.put(player.getUniqueId(), target.getUniqueId());

        return true;
    }
}