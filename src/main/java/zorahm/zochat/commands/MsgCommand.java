package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatConfig;
import zorahm.zochat.ChatPlugin;
import zorahm.zochat.MessageManager;
import zorahm.zochat.database.ChatLogger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

public class MsgCommand implements CommandExecutor {
    private final MessageManager messageManager;
    private final ChatConfig chatConfig;
    private final ChatPlugin plugin;
    private final ChatLogger chatLogger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final HashMap<UUID, UUID> lastMessages = new HashMap<>();
    private final HashMap<UUID, Long> lastMessageTime = new HashMap<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public MsgCommand(MessageManager messageManager, ChatConfig chatConfig, ChatPlugin plugin) {
        this.messageManager = messageManager;
        this.chatConfig = chatConfig;
        this.plugin = plugin;
        this.chatLogger = plugin.getChatLogger();
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
        plugin.logDebug(Level.INFO, "sending-pm", player.getName(), targetName, message);

        // Проверяем, есть ли игрок в онлайне
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUUID = null;

        if (target == null) {
            // Проверяем, существует ли игрок (оффлайн)
            targetUUID = Bukkit.getOfflinePlayer(targetName).getUniqueId();
            if (targetUUID == null || Bukkit.getOfflinePlayer(targetName).getName() == null) {
                sender.sendMessage(miniMessage.deserialize(
                        messageManager.getMessage("private-messages.player-not-found")
                                .replace("{player}", targetName)
                ));
                plugin.logStandard(Level.WARNING, "player-not-found", targetName);
                return true;
            }
            plugin.logDebug(Level.INFO, "target-offline", targetName, targetUUID.toString());
        } else {
            targetUUID = target.getUniqueId();
        }

        // Проверка антиспама
        if (chatConfig.isAntiSpamEnabled() && !player.hasPermission(chatConfig.getSpamBypassPermission())) {
            long now = System.currentTimeMillis();
            long lastTime = lastMessageTime.getOrDefault(player.getUniqueId(), 0L);
            int cooldown = chatConfig.getPrivateMessageCooldown();
            plugin.logDebug(Level.INFO, "pm-cooldown-check", player.getName(), now, lastTime, cooldown);
            if (now - lastTime < cooldown * 1000L) {
                player.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.spam-warning")));
                return true;
            }
            lastMessageTime.put(player.getUniqueId(), now);
            plugin.logDebug(Level.INFO, "pm-cooldown-updated", player.getName(), now);
        }

        String timestamp = timeFormatter.format(Instant.ofEpochMilli(System.currentTimeMillis()));
        Component messageComponent = miniMessage.deserialize(message)
                .hoverEvent(HoverEvent.showText(miniMessage.deserialize(
                        messageManager.getMessage("chat.message-timestamp").replace("{time}", timestamp))));

        // Если игрок оффлайн и включены оффлайн-сообщения
        if (target == null && chatConfig.isOfflineMessagesEnabled()) {
            chatLogger.saveOfflineMessage(player.getUniqueId(), targetUUID, message);
            player.sendMessage(miniMessage.deserialize(
                    messageManager.getMessage("private-messages.offline-sent")
                            .replace("{player}", targetName)
            ));
            plugin.logStandard(Level.INFO, "saved-offline-message", player.getName(), targetName, message);
            return true;
        } else if (target == null) {
            player.sendMessage(miniMessage.deserialize(
                    messageManager.getMessage("private-messages.offline-disabled")
            ));
            plugin.logStandard(Level.INFO, "offline-messages-disabled", targetName);
            return true;
        }

        // Формирование сообщений для онлайновых игроков
        String incomingFormat = chatConfig.getPrivateMessageFormat();
        String outgoingFormat = chatConfig.getPrivateMessageReplyFormat();

        Component incomingMessage = miniMessage.deserialize(
                        incomingFormat.replace("{player}", player.getName())
                ).replaceText(builder -> builder.matchLiteral("{message}").replacement(messageComponent))
                .clickEvent(ClickEvent.suggestCommand("/r "))
                .hoverEvent(HoverEvent.showText(miniMessage.deserialize(
                        messageManager.getMessage("chat.message-reply-hover"))));

        Component outgoingMessage = miniMessage.deserialize(
                        outgoingFormat.replace("{player}", target.getName())
                ).replaceText(builder -> builder.matchLiteral("{message}").replacement(messageComponent))
                .clickEvent(ClickEvent.suggestCommand("/msg " + target.getName() + " "))
                .hoverEvent(HoverEvent.showText(miniMessage.deserialize(
                        messageManager.getMessage("chat.message-resend-hover"))));

        target.sendMessage(incomingMessage);
        player.sendMessage(outgoingMessage);
        plugin.logStandard(Level.INFO, "sent-offline-message", player.getName(), target.getName(), message);

        lastMessages.put(target.getUniqueId(), player.getUniqueId());
        lastMessages.put(player.getUniqueId(), target.getUniqueId());

        return true;
    }

    public HashMap<UUID, UUID> getLastMessages() {
        return lastMessages;
    }

    public HashMap<UUID, Long> getLastMessagesTime() {
        return lastMessageTime;
    }

    public void clearLastMessaged(UUID playerId) {
        lastMessages.remove(playerId);
        lastMessages.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
        lastMessageTime.remove(playerId);
    }
}