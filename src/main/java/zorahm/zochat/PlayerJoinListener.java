package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import zorahm.zochat.database.ChatLogger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;

public class PlayerJoinListener implements Listener {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final MessageManager messageManager;
    private final WelcomeMessageManager welcomeMessageManager;
    private final ChatLogger chatLogger;
    private final LogMessageManager logMessageManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public PlayerJoinListener(ChatPlugin plugin, ChatConfig chatConfig, MessageManager messageManager, ChatLogger chatLogger) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.messageManager = messageManager;
        this.welcomeMessageManager = plugin.getWelcomeMessageManager();
        this.chatLogger = chatLogger;
        this.logMessageManager = plugin.getLogMessageManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.logDebug(Level.INFO, "player-join-checking", player.getName());

        // Отправка Welcome-сообщений
        if (welcomeMessageManager.isWelcomeMessagesEnabled()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    List<Component> welcomeMessages = welcomeMessageManager.getWelcomeMessages(player.getName());
                    if (!welcomeMessages.isEmpty()) {
                        plugin.logStandard(Level.INFO, "sending-welcome-messages", welcomeMessages.size(), player.getName());
                        for (Component message : welcomeMessages) {
                            player.sendMessage(message);
                        }
                    } else {
                        plugin.logDebug(Level.INFO, "no-welcome-messages", player.getName());
                    }
                }
            }.runTaskLater(plugin, welcomeMessageManager.getWelcomeMessagesDelay());
        } else {
            plugin.logDebug(Level.INFO, "welcome-messages-disabled", player.getName());
        }

        // Проверка и отправка оффлайн-сообщений
        List<ChatLogger.OfflineMessage> offlineMessages = chatLogger.getOfflineMessages(player.getUniqueId());
        plugin.logStandard(Level.INFO, "found-offline-messages", offlineMessages.size(), player.getName());

        if (!offlineMessages.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.offline-messages-header")
                    .replace("{count}", String.valueOf(offlineMessages.size()))));
            for (ChatLogger.OfflineMessage offlineMessage : offlineMessages) {
                String senderName = Bukkit.getOfflinePlayer(offlineMessage.getSender()).getName();
                if (senderName == null) {
                    senderName = "Unknown";
                    plugin.logStandard(Level.WARNING, "sender-uuid-not-found", offlineMessage.getSender().toString());
                }
                String timestamp = timeFormatter.format(Instant.ofEpochMilli(offlineMessage.getTimestamp().getTime()));
                Component messageComponent = miniMessage.deserialize(offlineMessage.getMessage())
                        .hoverEvent(HoverEvent.showText(miniMessage.deserialize(
                                messageManager.getMessage("chat.message-timestamp").replace("{time}", timestamp))));
                Component receiverMessage = miniMessage.deserialize(chatConfig.getPrivateMessageFormat()
                                .replace("{player}", senderName))
                        .replaceText(builder -> builder.matchLiteral("{message}").replacement(messageComponent));
                player.sendMessage(receiverMessage);
                plugin.logStandard(Level.INFO, "sent-offline-message", senderName, player.getName(), offlineMessage.getMessage());
            }
            chatLogger.deleteOfflineMessages(player.getUniqueId());
            plugin.logStandard(Level.INFO, "deleted-offline-messages", player.getName());
        } else {
            plugin.logDebug(Level.INFO, "no-offline-messages", player.getName());
        }
    }
}