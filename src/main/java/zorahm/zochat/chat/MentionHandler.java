package zorahm.zochat.chat;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import zorahm.zochat.config.ChatConfig;
import zorahm.zochat.util.Sounds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MentionHandler {
    private final Plugin plugin;
    private final ChatConfig chatConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // UNICODE_CHARACTER_CLASS so \w matches non-ASCII names too (e.g. Cyrillic @Ник), not just [a-zA-Z0-9_].
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)", Pattern.UNICODE_CHARACTER_CLASS);
    // The trailing lookahead stops "@all" from matching inside "@Allen" or "x@allmail.com" (which turned
    // them into @everyone). UNICODE_CASE so "@Все"/"@ЗДЕСЬ" match too — CASE_INSENSITIVE is ASCII-only.
    // Package-private for tests.
    static final Pattern EVERYONE_PATTERN = Pattern.compile(
            "@(everyone|все|all)(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    static final Pattern HERE_PATTERN = Pattern.compile(
            "@(here|здесь)(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final int MAX_MENTIONS_PER_MESSAGE = 5;

    public MentionHandler(Plugin plugin, ChatConfig chatConfig) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
    }

    public static class MentionResult {
        private final String processedMessage;
        private final List<Player> mentionedPlayers;
        private final boolean hasEveryoneMention;
        private final boolean hasHereMention;

        public MentionResult(String processedMessage, List<Player> mentionedPlayers,
                             boolean hasEveryoneMention, boolean hasHereMention) {
            this.processedMessage = processedMessage;
            this.mentionedPlayers = mentionedPlayers;
            this.hasEveryoneMention = hasEveryoneMention;
            this.hasHereMention = hasHereMention;
        }

        public String getProcessedMessage() {
            return processedMessage;
        }

        public List<Player> getMentionedPlayers() {
            return mentionedPlayers;
        }

        public boolean hasEveryoneMention() {
            return hasEveryoneMention;
        }

        public boolean hasHereMention() {
            return hasHereMention;
        }
    }

    public MentionResult processMentions(String message, Player sender) {
        String processedMessage = message;
        Set<Player> mentionedPlayers = new HashSet<>();
        boolean hasEveryoneMention = false;
        boolean hasHereMention = false;

        Matcher everyoneMatcher = EVERYONE_PATTERN.matcher(message);
        if (everyoneMatcher.find()) {
            if (sender.hasPermission(chatConfig.getMentionEveryonePermission())) {
                hasEveryoneMention = true;
                String everyoneFormat = chatConfig.getMentionEveryoneFormat();
                processedMessage = highlight(EVERYONE_PATTERN, processedMessage, everyoneFormat);
                mentionedPlayers.addAll(Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(sender))
                        .collect(Collectors.toList()));
            } else if (chatConfig.isDebugModeEnabled()) {
                plugin.getLogger().info(sender.getName() + " tried to use @everyone without permission");
            }
        }

        Matcher hereMatcher = HERE_PATTERN.matcher(processedMessage);
        if (hereMatcher.find()) {
            if (sender.hasPermission(chatConfig.getMentionHerePermission())) {
                hasHereMention = true;
                String hereFormat = chatConfig.getMentionHereFormat();
                processedMessage = highlight(HERE_PATTERN, processedMessage, hereFormat);

                int radius = chatConfig.getMentionHereRadius();
                Location senderLoc = sender.getLocation();
                mentionedPlayers.addAll(Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(sender))
                        .filter(p -> p.getWorld().equals(sender.getWorld()))
                        .filter(p -> p.getLocation().distanceSquared(senderLoc) <= radius * radius)
                        .collect(Collectors.toList()));
            } else if (chatConfig.isDebugModeEnabled()) {
                plugin.getLogger().info(sender.getName() + " tried to use @here without permission");
            }
        }

        if (!hasEveryoneMention && mentionedPlayers.size() < MAX_MENTIONS_PER_MESSAGE) {
            Matcher matcher = MENTION_PATTERN.matcher(processedMessage);
            StringBuffer sb = new StringBuffer();

            while (matcher.find() && mentionedPlayers.size() < MAX_MENTIONS_PER_MESSAGE) {
                String mentionedName = matcher.group(1);

                if (mentionedName.equalsIgnoreCase("everyone") || mentionedName.equalsIgnoreCase("все") ||
                        mentionedName.equalsIgnoreCase("all") || mentionedName.equalsIgnoreCase("here") ||
                        mentionedName.equalsIgnoreCase("здесь")) {
                    continue;
                }

                Player mentionedPlayer = findPlayer(mentionedName);
                if (mentionedPlayer != null && !mentionedPlayer.equals(sender) &&
                        !mentionedPlayers.contains(mentionedPlayer)) {

                    String mentionFormat = chatConfig.getMentionFormat()
                            .replace("{player}", mentionedPlayer.getName());
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(mentionFormat));
                    mentionedPlayers.add(mentionedPlayer);
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                }
            }
            matcher.appendTail(sb);
            processedMessage = sb.toString();
        }

        return new MentionResult(processedMessage, new ArrayList<>(mentionedPlayers),
                hasEveryoneMention, hasHereMention);
    }

    // quoteReplacement: the format comes from config, and a raw replaceAll treats '$' and '\' in it as
    // group references — a "$" in the format threw or mangled the message.
    static String highlight(Pattern pattern, String message, String format) {
        return pattern.matcher(message).replaceAll(Matcher.quoteReplacement(format));
    }

    private Player findPlayer(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }

        List<Player> matches = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getName().toLowerCase().startsWith(name.toLowerCase()))
                .collect(Collectors.toList());

        return matches.size() == 1 ? matches.get(0) : null;
    }

    public void notifyMentionedPlayers(List<Player> mentionedPlayers, MentionType type) {
        String message = getMentionMessage(type);
        String soundName = chatConfig.getMentionSound();

        for (Player mentionedPlayer : mentionedPlayers) {
            Sounds.parse(soundName).ifPresent(s ->
                    mentionedPlayer.playSound(mentionedPlayer.getLocation(), s, 1.0f, 1.0f));
            mentionedPlayer.sendActionBar(miniMessage.deserialize(message));
        }
    }

    private String getMentionMessage(MentionType type) {
        switch (type) {
            case EVERYONE:
                return chatConfig.getMentionEveryoneMessage();
            case HERE:
                return chatConfig.getMentionHereMessage();
            default:
                return chatConfig.getMentionMessage();
        }
    }

    public List<String> getPlayerSuggestions(String partial) {
        String lower = partial.toLowerCase();
        List<String> suggestions = new ArrayList<>();

        if ("everyone".startsWith(lower) || "все".startsWith(lower)) {
            suggestions.add("everyone");
        }
        if ("here".startsWith(lower) || "здесь".startsWith(lower)) {
            suggestions.add("here");
        }

        suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .sorted()
                .limit(10)
                .collect(Collectors.toList()));

        return suggestions;
    }

    public enum MentionType {
        NORMAL,
        EVERYONE,
        HERE
    }
}
