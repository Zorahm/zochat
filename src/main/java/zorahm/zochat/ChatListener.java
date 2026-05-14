package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

public class ChatListener implements Listener {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final ChatLogger chatLogger;
    private final MessageManager messageManager;
    private final MentionHandler mentionHandler;
    private final PlaceholderManager placeholderManager;
    private final BannedWordsManager bannedWordsManager;
    private final HashMap<UUID, Long> lastMessageTime;
    private final LuckPerms luckPerms;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public ChatListener(ChatPlugin plugin, ChatConfig chatConfig, ChatLogger chatLogger, MessageManager messageManager, HashMap<UUID, Long> lastMessageTime, PlaceholderManager placeholderManager, BannedWordsManager bannedWordsManager) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.chatLogger = chatLogger;
        this.messageManager = messageManager;
        this.mentionHandler = new MentionHandler(plugin, chatConfig);
        this.placeholderManager = placeholderManager;
        this.bannedWordsManager = bannedWordsManager;
        this.lastMessageTime = lastMessageTime;
        this.luckPerms = LuckPermsProvider.get();
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        UUID playerId = player.getUniqueId();

        boolean forceGlobal = false;
        if (message.startsWith("!")) {
            message = message.substring(1).trim();
            forceGlobal = true;
            event.setMessage(message);
        }

        chatLogger.logMessage(player, message);

        if (chatConfig.isAntiSpamEnabled() && !player.hasPermission(chatConfig.getSpamBypassPermission())) {
            long now = System.currentTimeMillis();
            long lastTime = lastMessageTime.getOrDefault(playerId, 0L);
            int cooldown = forceGlobal || !chatConfig.isLocalChatEnabled() ? chatConfig.getGlobalChatCooldown() : chatConfig.getLocalChatCooldown();
            plugin.logDebug(Level.INFO, "chat-cooldown-check",
                    new Object[]{player.getName(), now, lastTime, cooldown});
            if (now - lastTime < cooldown * 1000L) {
                player.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.spam-warning")));
                event.setCancelled(true);
                return;
            }
            lastMessageTime.put(playerId, now);
            plugin.logDebug(Level.INFO, "chat-cooldown-updated", new Object[]{player.getName(), now});
        }

        // Проверка запрещенных слов через улучшенный фильтр
        BannedWordsManager.FilterResult filterResult = bannedWordsManager.checkMessage(message);
        if (filterResult.isBlocked()) {
            player.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.banned-word")));
            event.setCancelled(true);
            plugin.logDebug(Level.INFO, "chat-banned-word",
                    new Object[]{player.getName(), filterResult.getMatchedWord()});
            return;
        }
        // Если режим замены - используем отфильтрованное сообщение
        if (filterResult.getProcessedMessage() != null && !filterResult.getProcessedMessage().equals(message)) {
            message = filterResult.getProcessedMessage();
            event.setMessage(message);
        }

        // Обработка плейсхолдеров
        plugin.logDebug(Level.INFO, "chat-processing-placeholders", new Object[]{player.getName(), message});
        Component processedMessageComponent = placeholderManager.processPlaceholders(player, message);
        plugin.logDebug(Level.INFO, "chat-processed-component", new Object[]{player.getName(), miniMessage.serialize(processedMessageComponent)});

        // Обработка упоминаний
        MentionHandler.MentionResult mentionResult = mentionHandler.processMentions(miniMessage.serialize(processedMessageComponent), player);
        processedMessageComponent = miniMessage.deserialize(mentionResult.getProcessedMessage());

        // Определяем тип упоминания
        MentionHandler.MentionType mentionType = MentionHandler.MentionType.NORMAL;
        if (mentionResult.hasEveryoneMention()) {
            mentionType = MentionHandler.MentionType.EVERYONE;
        } else if (mentionResult.hasHereMention()) {
            mentionType = MentionHandler.MentionType.HERE;
        }

        mentionHandler.notifyMentionedPlayers(mentionResult.getMentionedPlayers(), mentionType);

        User user = luckPerms.getUserManager().getUser(playerId);
        String prefix = (user != null && user.getCachedData().getMetaData().getPrefix() != null)
                ? user.getCachedData().getMetaData().getPrefix() : "";
        String suffix = (user != null && user.getCachedData().getMetaData().getSuffix() != null)
                ? user.getCachedData().getMetaData().getSuffix() : "";

        String format;
        boolean sendToLocalChat = !forceGlobal && chatConfig.isLocalChatEnabled();

        if (sendToLocalChat) {
            format = chatConfig.getLocalChatFormat();
        } else {
            format = chatConfig.getGlobalChatFormat();
        }

        String timestamp = timeFormatter.format(Instant.ofEpochMilli(System.currentTimeMillis()));
        Component prefixComponent = miniMessage.deserialize(prefix);
        Component suffixComponent = miniMessage.deserialize(suffix);
        Component playerNameComponent = Component.text(player.getName())
                .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "))
                .hoverEvent(HoverEvent.showText(miniMessage.deserialize(
                        messageManager.getMessage("chat.message-player").replace("{player}", player.getName()))));
        Component messageComponent = processedMessageComponent
                .hoverEvent(HoverEvent.showText(miniMessage.deserialize(
                        messageManager.getMessage("chat.message-timestamp").replace("{time}", timestamp))));

        Component finalMessage = miniMessage.deserialize(format)
                .replaceText(builder -> builder.matchLiteral("{prefix}").replacement(prefixComponent))
                .replaceText(builder -> builder.matchLiteral("{suffix}").replacement(suffixComponent))
                .replaceText(builder -> builder.matchLiteral("{player}").replacement(playerNameComponent))
                .replaceText(builder -> builder.matchLiteral("{message}").replacement(messageComponent));

        plugin.logDebug(Level.INFO, "chat-format-template", new Object[]{format});
        plugin.logDebug(Level.INFO, "chat-format-details",
                new Object[]{prefix, suffix, player.getName(), miniMessage.serialize(processedMessageComponent)});

        if (sendToLocalChat) {
            int radius = chatConfig.getLocalChatRadius();
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                if (recipient.getWorld().equals(player.getWorld()) &&
                        recipient.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                    recipient.sendMessage(finalMessage);
                }
            }
        } else {
            Bukkit.getServer().broadcast(finalMessage);
        }

        event.setCancelled(true);
    }
}