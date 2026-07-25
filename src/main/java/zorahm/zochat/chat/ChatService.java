package zorahm.zochat.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import zorahm.zochat.bubble.BubbleService;
import zorahm.zochat.config.ChatConfig;
import zorahm.zochat.config.Messages;
import zorahm.zochat.storage.ChatLogRepository;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class ChatService {
    private final ChatConfig config;
    private final Messages messages;
    private final LuckPerms luckPerms;
    private final BannedWordsFilter bannedWords;
    private final MentionHandler mentions;
    private final PlaceholderService placeholders;
    private final PapiHook papi;
    private final ChatLogRepository chatLog;
    private final CooldownService cooldowns;
    private final BubbleService bubble;

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public ChatService(ChatConfig config, Messages messages, LuckPerms luckPerms,
                       BannedWordsFilter bannedWords, MentionHandler mentions,
                       PlaceholderService placeholders, PapiHook papi, ChatLogRepository chatLog,
                       CooldownService cooldowns, BubbleService bubble) {
        this.config = config;
        this.messages = messages;
        this.luckPerms = luckPerms;
        this.bannedWords = bannedWords;
        this.mentions = mentions;
        this.placeholders = placeholders;
        this.papi = papi;
        this.chatLog = chatLog;
        this.cooldowns = cooldowns;
        this.bubble = bubble;
    }

    public void send(Player sender, String rawMessage, ChatChannel channel) {
        UUID playerId = sender.getUniqueId();

        // Filter before the cooldown so a message rejected for a banned word doesn't burn the cooldown.
        BannedWordsFilter.FilterResult filterResult = bannedWords.checkMessage(rawMessage);
        if (filterResult.isBlocked()) {
            sender.sendMessage(mm.deserialize(messages.get("chat.banned-word")));
            return;
        }
        String message = filterResult.getProcessedMessage() != null
                ? filterResult.getProcessedMessage() : rawMessage;

        if (config.isAntiSpamEnabled() && !sender.hasPermission(config.getSpamBypassPermission())) {
            CooldownService.Channel cdChannel = (channel == ChatChannel.GLOBAL)
                    ? CooldownService.Channel.GLOBAL
                    : CooldownService.Channel.LOCAL;
            int cooldown = (channel == ChatChannel.GLOBAL)
                    ? config.getGlobalChatCooldown()
                    : config.getLocalChatCooldown();
            if (cooldowns.isOnCooldown(playerId, cdChannel, cooldown)) {
                sender.sendMessage(mm.deserialize(messages.get("chat.spam-warning")));
                return;
            }
        }

        // Players typing %...% is opt-in and permission-gated (abuse risk). Expand into a separate
        // string so the DB log below keeps the player's literal text, not the resolved placeholders.
        String displayMessage = message;
        if (config.isPlaceholderApiEnabled() && config.isPlaceholderApiPlayerMessagesEnabled()
                && sender.hasPermission(config.getPlaceholderApiPlayerPermission())) {
            displayMessage = papi.apply(sender, message);
        }

        // Escape once, then stay at the string level until the final deserialize. Mentions run
        // BEFORE placeholder expansion so an '@' inside an expanded value (a PAPI value, an anvil-
        // renamed item in ^item) can never trigger a mention; it also drops the old fragile
        // Component -> serialize -> regex -> deserialize round-trip.
        String escaped = mm.escapeTags(displayMessage);
        MentionHandler.MentionResult mentionResult = mentions.processMentions(escaped, sender);
        Component processedMessageComponent =
                placeholders.processEscaped(sender, mentionResult.getProcessedMessage());

        MentionHandler.MentionType mentionType = MentionHandler.MentionType.NORMAL;
        if (mentionResult.hasEveryoneMention()) {
            mentionType = MentionHandler.MentionType.EVERYONE;
        } else if (mentionResult.hasHereMention()) {
            mentionType = MentionHandler.MentionType.HERE;
        }
        mentions.notifyMentionedPlayers(mentionResult.getMentionedPlayers(), mentionType);

        chatLog.log(sender.getUniqueId(), message);

        User user = luckPerms.getUserManager().getUser(playerId);
        String prefix = (user != null && user.getCachedData().getMetaData().getPrefix() != null)
                ? user.getCachedData().getMetaData().getPrefix() : "";
        String suffix = (user != null && user.getCachedData().getMetaData().getSuffix() != null)
                ? user.getCachedData().getMetaData().getSuffix() : "";

        String format = (channel == ChatChannel.LOCAL)
                ? config.getLocalChatFormat()
                : config.getGlobalChatFormat();

        String timestamp = time.format(Instant.ofEpochMilli(System.currentTimeMillis()));

        // Inline prefix/suffix as MiniMessage so an unclosed colour/gradient in them flows into
        // what follows in the format — e.g. a <#hex> suffix recolours {message}. Supports both
        // legacy codes and MiniMessage tags in the LuckPerms meta (PrefixFormatter.toMiniMessage).
        String inlined = format
                .replace("{prefix}", PrefixFormatter.toMiniMessage(prefix))
                .replace("{suffix}", PrefixFormatter.toMiniMessage(suffix));

        // Expand %...% in the format (e.g. %vault_prefix%, %player_world%). The {player}/{message}
        // literals contain no '%' so they survive to the Component replacements below untouched.
        if (config.isPlaceholderApiEnabled() && config.isPlaceholderApiFormatEnabled()) {
            inlined = papi.apply(sender, inlined);
        }

        Component playerNameComponent = Component.text(sender.getName())
                .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "))
                .hoverEvent(HoverEvent.showText(mm.deserialize(
                        messages.get("chat.message-player").replace("{player}", sender.getName()))));
        Component messageComponent = processedMessageComponent
                .hoverEvent(HoverEvent.showText(mm.deserialize(
                        messages.get("chat.message-timestamp").replace("{time}", timestamp))));

        // {player}/{message} stay components so player input is never parsed as MiniMessage,
        // yet they still inherit any open colour from the inlined prefix/suffix.
        Component finalMessage = mm.deserialize(inlined)
                .replaceText(b -> b.matchLiteral("{player}").replacement(playerNameComponent))
                .replaceText(b -> b.matchLiteral("{message}").replacement(messageComponent));

        if (channel == ChatChannel.LOCAL) {
            deliverLocal(sender, finalMessage);
        } else {
            Bukkit.getServer().broadcast(finalMessage);
        }

        // Bubble is shown in addition to chat; it gates itself on enabled + CHAT trigger. Pass the
        // filtered message so the bubble censors banned words just like chat does.
        bubble.onChat(sender, message);
    }

    private void deliverLocal(Player sender, Component message) {
        int radius = config.getLocalChatRadius();
        for (Player r : Bukkit.getOnlinePlayers()) {
            if (r.getWorld().equals(sender.getWorld())
                    && r.getLocation().distanceSquared(sender.getLocation()) <= (double) radius * radius) {
                r.sendMessage(message);
            }
        }
    }
}
