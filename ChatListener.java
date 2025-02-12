package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener implements Listener {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final ChatLogger chatLogger; // Добавлено поле chatLogger
    private final HashMap<UUID, Long> lastMessageTime = new HashMap<>();
    private final LuckPerms luckPerms;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    public ChatListener(ChatPlugin plugin, ChatConfig chatConfig, ChatLogger chatLogger) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.chatLogger = chatLogger; // Инициализация chatLogger
        this.luckPerms = LuckPermsProvider.get();
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Логируем сообщение в базу данных
        chatLogger.logMessage(player, message);

        // Антиспам
        long now = System.currentTimeMillis();
        long lastTime = lastMessageTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastTime < chatConfig.getSpamCooldown() * 1000L) {
            player.sendMessage(miniMessage.deserialize("<red>Пожалуйста, не спамьте!</red>"));
            event.setCancelled(true);
            return;
        }
        lastMessageTime.put(player.getUniqueId(), now);

        // Проверка запрещенных слов
        for (String word : message.split(" ")) {
            if (chatConfig.isBannedWord(word)) {
                player.sendMessage(miniMessage.deserialize("<red>Сообщение содержит запрещенные слова!</red>"));
                event.setCancelled(true);
                return;
            }
        }

        // Поиск упоминаний через @
        Matcher matcher = MENTION_PATTERN.matcher(message);
        while (matcher.find()) {
            String mentionedName = matcher.group(1);
            Player mentionedPlayer = Bukkit.getPlayerExact(mentionedName);

            if (mentionedPlayer != null) {
                // Форматируем упоминание
                String mentionFormat = chatConfig.getMentionFormat().replace("{player}", mentionedPlayer.getName());
                Component mentionComponent = miniMessage.deserialize(mentionFormat);

                // Заменяем @ник на форматированный текст
                message = message.replace("@" + mentionedName, PlainTextComponentSerializer.plainText().serialize(mentionComponent));

                // Проигрываем звук (проверяем его наличие)
                try {
                    Sound sound = Sound.valueOf(chatConfig.getMentionSound().toUpperCase());
                    mentionedPlayer.playSound(mentionedPlayer.getLocation(), sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Звук '" + chatConfig.getMentionSound() + "' не найден или указан неверно!");
                }

                // Отправляем уведомление через Action Bar
                mentionedPlayer.sendActionBar(miniMessage.deserialize(chatConfig.getMentionMessage()));
            }
        }

        // Получаем LuckPerms пользователя
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        String prefix = (user != null && user.getCachedData().getMetaData().getPrefix() != null)
                ? user.getCachedData().getMetaData().getPrefix() : "";
        String suffix = (user != null && user.getCachedData().getMetaData().getSuffix() != null)
                ? user.getCachedData().getMetaData().getSuffix() : "";

        String formattedMessage = chatConfig.getChatFormat()
                .replace("{prefix}", prefix)
                .replace("{player}", player.getName())
                .replace("{suffix}", suffix)
                .replace("{message}", message);

        // Логирование для проверки
        plugin.getLogger().info("Формат из конфига: " + chatConfig.getChatFormat());
        plugin.getLogger().info("Отформатированное сообщение: " + formattedMessage);

        // Проверка MiniMessage
        Component parsedMessage = miniMessage.deserialize(formattedMessage);
        plugin.getLogger().info("Отправляемое сообщение (проверка): " + PlainTextComponentSerializer.plainText().serialize(parsedMessage));

        // Отправка сообщения
        Bukkit.getServer().broadcast(parsedMessage);

        // Отмена стандартного чата
        event.setCancelled(true);

    }
}
