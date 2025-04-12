package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import zorahm.zochat.database.ChatLogger;

import java.util.HashMap;
import java.util.UUID;

public class ChatListener implements Listener {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final ChatLogger chatLogger;
    private final MessageManager messageManager;
    private final MentionHandler mentionHandler;
    private final HashMap<UUID, Long> lastMessageTime = new HashMap<>();
    private final LuckPerms luckPerms;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatListener(ChatPlugin plugin, ChatConfig chatConfig, ChatLogger chatLogger, MessageManager messageManager) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.chatLogger = chatLogger;
        this.messageManager = messageManager;
        this.mentionHandler = new MentionHandler(plugin, chatConfig);
        this.luckPerms = LuckPermsProvider.get();
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        UUID playerId = player.getUniqueId();

        // Проверка на ! для глобального чата
        boolean forceGlobal = false;
        if (message.startsWith("!")) {
            message = message.substring(1).trim();
            forceGlobal = true;
            event.setMessage(message);
        }

        // Логируем сообщение
        chatLogger.logMessage(player, message);

        // Антиспам
        long now = System.currentTimeMillis();
        long lastTime = lastMessageTime.getOrDefault(playerId, 0L);
        if (now - lastTime < chatConfig.getSpamCooldown() * 1000L) {
            player.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.spam-warning")));
            event.setCancelled(true);
            return;
        }
        lastMessageTime.put(playerId, now);

        // Проверка запрещённых слов
        for (String word : message.split(" ")) {
            if (chatConfig.isBannedWord(word)) {
                player.sendMessage(miniMessage.deserialize("<red>Сообщение содержит запрещённые слова!</red>"));
                event.setCancelled(true);
                return;
            }
        }

        // Обработка упоминаний
        MentionHandler.MentionResult mentionResult = mentionHandler.processMentions(message);
        String processedMessage = mentionResult.getProcessedMessage();
        mentionHandler.notifyMentionedPlayers(mentionResult.getMentionedPlayers());

        // Получаем префикс и суффикс из LuckPerms
        User user = luckPerms.getUserManager().getUser(playerId);
        String prefix = (user != null && user.getCachedData().getMetaData().getPrefix() != null)
                ? user.getCachedData().getMetaData().getPrefix() : "";
        String suffix = (user != null && user.getCachedData().getMetaData().getSuffix() != null)
                ? user.getCachedData().getMetaData().getSuffix() : "";

        // Определяем, куда отправлять сообщение
        String format;
        boolean sendToLocalChat = !forceGlobal && chatConfig.isLocalChatEnabled();

        if (sendToLocalChat) {
            format = chatConfig.getLocalChatFormat();
        } else {
            format = chatConfig.getGlobalChatFormat();
        }

        // Форматируем сообщение
        String formattedMessage = format
                .replace("{prefix}", prefix)
                .replace("{player}", player.getName())
                .replace("{suffix}", suffix)
                .replace("{message}", processedMessage);

        Component parsedMessage = miniMessage.deserialize(formattedMessage);

        // Отправка сообщения
        if (sendToLocalChat) {
            int radius = chatConfig.getLocalChatRadius();
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                if (recipient.getWorld().equals(player.getWorld()) &&
                        recipient.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                    recipient.sendMessage(parsedMessage);
                }
            }
        } else {
            Bukkit.getServer().broadcast(parsedMessage);
        }

        // Отмена стандартного чата
        event.setCancelled(true);
    }
}