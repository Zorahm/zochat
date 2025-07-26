package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatConfig;
import zorahm.zochat.MentionHandler;
import zorahm.zochat.MessageManager;

import java.util.HashMap;
import java.util.UUID;

public class MsgCommand implements CommandExecutor {
    private final MessageManager messageManager;
    private final ChatConfig chatConfig;
    private final MentionHandler mentionHandler;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final HashMap<UUID, UUID> lastMessages = new HashMap<>();

    public MsgCommand(MessageManager messageManager, ChatConfig chatConfig) {
        this.messageManager = messageManager;
        this.chatConfig = chatConfig;
        this.mentionHandler = new MentionHandler(null, chatConfig); // plugin не нужен, так как не используем логи
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("errors.only-players")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("private-messages.usage")));
            return true;
        }

        Player player = (Player) sender;
        String targetName = args[0];
        String message = String.join(" ", args).substring(targetName.length() + 1);

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(miniMessage.deserialize(
                    messageManager.getMessage("private-messages.player-not-found")
                            .replace("{player}", targetName)
            ));
            return true;
        }

        // Обработка упоминаний
        MentionHandler.MentionResult mentionResult = mentionHandler.processMentions(message);
        String processedMessage = mentionResult.getProcessedMessage();
        mentionHandler.notifyMentionedPlayers(mentionResult.getMentionedPlayers());

        // Получаем форматы из ChatConfig
        String incomingFormat = chatConfig.getPrivateMessageFormat();
        String outgoingFormat = chatConfig.getPrivateMessageReplyFormat();

        Component incomingMessage = miniMessage.deserialize(
                incomingFormat
                        .replace("{player}", player.getName())
                        .replace("{message}", processedMessage)
        );

        Component outgoingMessage = miniMessage.deserialize(
                outgoingFormat
                        .replace("{player}", target.getName())
                        .replace("{message}", processedMessage)
        );

        target.sendMessage(incomingMessage);
        player.sendMessage(outgoingMessage);

        lastMessages.put(target.getUniqueId(), player.getUniqueId());

        return true;
    }

    public HashMap<UUID, UUID> getLastMessages() {
        return lastMessages;
    }
}