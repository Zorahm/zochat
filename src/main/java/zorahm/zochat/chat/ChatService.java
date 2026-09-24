package zorahm.zochat.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import zorahm.zochat.bubble.BubbleService;
import zorahm.zochat.config.ChatConfig;
import zorahm.zochat.config.Messages;
import zorahm.zochat.storage.ChatLogRepository;
import zorahm.zochat.util.PlayerText;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private static final MiniMessage mm = MiniMessage.miniMessage();
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
        // A lone "!" (global prefix with no text) arrives here empty — don't broadcast a blank line.
        if (rawMessage.isBlank() || !mayUse(sender, channel)) {
            return;
        }

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

        // Escape once, then stay at the string level until the final deserialize. Mentions run
        // BEFORE ^placeholder expansion so an '@' inside an expanded value (an anvil-renamed item in
        // ^item) can never trigger a mention; it also drops the old fragile
        // Component -> serialize -> regex -> deserialize round-trip.
        // Players typing %...% is opt-in and permission-gated (abuse risk); the DB log below keeps the
        // player's literal text either way.
        boolean playerPapi = config.isPlaceholderApiEnabled() && config.isPlaceholderApiPlayerMessagesEnabled()
                && sender.hasPermission(config.getPlaceholderApiPlayerPermission());
        String escaped = playerPapi ? papi.escapeWithPlaceholders(sender, message) : PlayerText.escape(message);
        MentionHandler.MentionResult mentionResult = mentions.processMentions(escaped, sender);
        Component processedMessageComponent =
                placeholders.processEscaped(sender, mentionResult.getProcessedMessage());

        MentionHandler.MentionType mentionType = MentionHandler.MentionType.NORMAL;
        if (mentionResult.hasEveryoneMention()) {
            mentionType = MentionHandler.MentionType.EVERYONE;
        } else if (mentionResult.hasHereMention()) {
            mentionType = MentionHandler.MentionType.HERE;
        }
        Collection<? extends Player> recipients = (channel == ChatChannel.LOCAL)
                ? localRecipients(sender, Bukkit.getOnlinePlayers(), config.getLocalChatRadius())
                : Bukkit.getOnlinePlayers();
        // Only ping players who actually receive the message: in local chat a mention (or @everyone)
        // used to sound for players out of range or in other worlds who could never read it.
        mentions.notifyMentionedPlayers(onlyRecipients(mentionResult.getMentionedPlayers(), recipients), mentionType);

        chatLog.log(sender.getUniqueId(), message);

        User user = luckPerms.getUserManager().getUser(playerId);
        String prefix = (user != null && user.getCachedData().getMetaData().getPrefix() != null)
                ? user.getCachedData().getMetaData().getPrefix() : "";
        String suffix = (user != null && user.getCachedData().getMetaData().getSuffix() != null)
                ? user.getCachedData().getMetaData().getSuffix() : "";

        String format = (channel == ChatChannel.LOCAL)
                ? config.getLocalChatFormat()
                : config.getGlobalChatFormat();
        format = LuckPermsMeta.expand(format,
                key -> user != null ? user.getCachedData().getMetaData().getMetaValue(key) : null);

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

        Component finalMessage = render(inlined, playerNameComponent, messageComponent);

        if (channel == ChatChannel.LOCAL) {
            for (Player r : recipients) {
                r.sendMessage(finalMessage);
            }
            // Global goes through Bukkit.broadcast, which reaches the console; local chat never did,
            // so it was missing from the server log entirely.
            Bukkit.getConsoleSender().sendMessage(finalMessage);
        } else {
            Bukkit.getServer().broadcast(finalMessage);
        }

        // Bubble is shown in addition to chat; it gates itself on enabled + CHAT trigger. It gets the
        // processed message (filtered, mentions and ^placeholders rendered) — the raw text left a
        // literal "^loc" floating above the player's head.
        bubble.onChat(sender, processedMessageComponent);
    }

    private boolean mayUse(Player sender, ChatChannel channel) {
        boolean global = channel == ChatChannel.GLOBAL;
        // global-chat.enabled and the zochat.global/zochat.local nodes used to be declared but never read.
        if (global ? !config.isGlobalChatEnabled() : !config.isLocalChatEnabled()) {
            sender.sendMessage(messages.component(global ? "chat.global-disabled" : "chat.local-disabled"));
            return false;
        }
        if (!sender.hasPermission(global ? "zochat.global" : "zochat.local")) {
            sender.sendMessage(messages.component("errors.no-permission"));
            return false;
        }
        return true;
    }

    // {player}/{message} become inserted-component tags rather than replaceText() targets: an unclosed
    // <gradient> from the prefix/suffix splits the literal "{player}" into one component per character,
    // so replaceText never found it and the raw token was shown. As tags they're still components (player
    // input is never parsed as MiniMessage) and still inherit the open colour/gradient around them.
    // Package-private + static so it's unit-testable without a live server.
    static Component render(String format, Component player, Component message) {
        String tagged = format
                .replace("{player}", "<zochat_player>")
                .replace("{message}", "<zochat_message>");
        return mm.deserialize(tagged, TagResolver.resolver(
                Placeholder.component("zochat_player", player),
                Placeholder.component("zochat_message", message)));
    }

    static List<Player> localRecipients(Player sender, Collection<? extends Player> online, int radius) {
        double radiusSq = (double) radius * radius;
        List<Player> out = new ArrayList<>();
        for (Player r : online) {
            // Same-world check first: distanceSquared throws across worlds.
            if (r.getWorld().equals(sender.getWorld())
                    && r.getLocation().distanceSquared(sender.getLocation()) <= radiusSq) {
                out.add(r);
            }
        }
        return out;
    }

    static List<Player> onlyRecipients(List<Player> mentioned, Collection<? extends Player> recipients) {
        // Hash once: @everyone mentions every online player, so a linear contains() per mention is O(n²).
        Set<Player> heard = new HashSet<>(recipients);
        List<Player> out = new ArrayList<>(mentioned.size());
        for (Player p : mentioned) {
            if (heard.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }
}
