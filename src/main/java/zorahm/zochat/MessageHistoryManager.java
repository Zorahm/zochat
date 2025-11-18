package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import zorahm.zochat.database.ChatLogger;

import java.util.List;
import java.util.UUID;

/**
 * Менеджер для работы с историей личных сообщений
 */
public class MessageHistoryManager {
    private final ChatPlugin plugin;
    private final ChatLogger chatLogger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageHistoryManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.chatLogger = plugin.getChatLogger();
    }

    /**
     * Отображает историю сообщений между двумя игроками
     */
    public void showHistory(Player viewer, UUID targetUUID) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ChatLogger.PrivateMessageHistory> history =
                    chatLogger.getPrivateMessageHistory(viewer.getUniqueId(), targetUUID);

            // Возвращаемся в главный поток для отправки сообщений
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (history.isEmpty()) {
                    viewer.sendMessage(miniMessage.deserialize(
                            "<red>История сообщений с этим игроком пуста.</red>"));
                    return;
                }

                Player target = Bukkit.getPlayer(targetUUID);
                String targetName = target != null ? target.getName() : "Неизвестный игрок";

                viewer.sendMessage(miniMessage.deserialize(
                        "<gradient:#f6a0d3:#b47ee5>════════ История сообщений с " + targetName + " ════════</gradient>"));

                for (ChatLogger.PrivateMessageHistory msg : history) {
                    String formattedTime = msg.getFormattedTime();
                    boolean isSender = msg.getSender().equals(viewer.getUniqueId());

                    Component messageComponent;
                    if (isSender) {
                        // Исходящее сообщение
                        messageComponent = miniMessage.deserialize(
                                "<gray>[" + formattedTime + "]</gray> " +
                                        "<gradient:#b47ee5:#f6a0d3>Вы → " + targetName + ":</gradient> " +
                                        "<white>" + msg.getMessage() + "</white>");
                    } else {
                        // Входящее сообщение
                        messageComponent = miniMessage.deserialize(
                                "<gray>[" + formattedTime + "]</gray> " +
                                        "<gradient:#f6a0d3:#b47ee5>" + targetName + " → Вы:</gradient> " +
                                        "<white>" + msg.getMessage() + "</white>");
                    }

                    viewer.sendMessage(messageComponent);
                }

                viewer.sendMessage(miniMessage.deserialize(
                        "<gradient:#f6a0d3:#b47ee5>═══════════════════════════════════════════════</gradient>"));
            });
        });
    }

    /**
     * Сохраняет личное сообщение в историю с пометкой "✓ Доставлено"
     */
    public void saveMessageWithDeliveryStatus(UUID sender, UUID receiver, String message, Player receiverPlayer) {
        // Сохраняем асинхронно
        chatLogger.savePrivateMessageAsync(sender, receiver, message);

        // Отправляем уведомление отправителю, если он онлайн
        Player senderPlayer = Bukkit.getPlayer(sender);
        if (senderPlayer != null && receiverPlayer != null) {
            // Показываем статус доставки
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                senderPlayer.sendActionBar(miniMessage.deserialize("<green>✓ Доставлено</green>"));
            }, 10L); // Задержка 0.5 сек для эффекта
        }
    }
}
