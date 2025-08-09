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
    private final HashMap<UUID, Long> lastMessageTime;
    private final LuckPerms luckPerms;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public ChatListener(ChatPlugin plugin, ChatConfig chatConfig, ChatLogger chatLogger, MessageManager messageManager, HashMap<UUID, Long> lastMessageTime, PlaceholderManager placeholderManager) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.chatLogger = chatLogger;
        this.messageManager = messageManager;
        this.mentionHandler = new MentionHandler(plugin, chatConfig);
        this.placeholderManager = placeholderManager;
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
            plugin.getLogger().log(Level.INFO, "Checking cooldown for {0}: now={1}, lastTime={2}, cooldown={3}s",
                    new Object[]{player.getName(), now, lastTime, cooldown});
            if (now - lastTime < cooldown * 1000L) {
                player.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.spam-warning")));
                event.setCancelled(true);
                return;
            }
            lastMessageTime.put(playerId, now);
            plugin.getLogger().log(Level.INFO, "Updated lastMessageTime for {0}: {1}", new Object[]{player.getName(), now});
        }

        for (String word : message.split(" ")) {
            if (chatConfig.isBannedWord(word)) {
                player.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.banned-word")));
                event.setCancelled(true);
                return;
            }
        }

        // Обработка плейсхолдеров
        plugin.getLogger().log(Level.INFO, "Processing placeholders for {0}: {1}", new Object[]{player.getName(), message});
        Component processedMessageComponent = placeholderManager.processPlaceholders(player, message);
        plugin.getLogger().log(Level.INFO, "Processed message component for {0}: {1}", new Object[]{player.getName(), miniMessage.serialize(processedMessageComponent)});

        // Обработка упоминаний
        MentionHandler.MentionResult mentionResult = mentionHandler.processMentions(miniMessage.serialize(processedMessageComponent));
        processedMessageComponent = miniMessage.deserialize(mentionResult.getProcessedMessage());
        mentionHandler.notifyMentionedPlayers(mentionResult.getMentionedPlayers());

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

        plugin.getLogger().log(Level.INFO, "Chat message format: {0}", format);
        plugin.getLogger().log(Level.INFO, "Prefix: {0}, Suffix: {1}, Player: {2}, Message: {3}",
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