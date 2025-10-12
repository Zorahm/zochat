package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Улучшенный обработчик упоминаний
 * Поддерживает:
 * - @PlayerName - упоминание конкретного игрока
 * - @everyone - упоминание всех (требует право)
 * - @here - упоминание игроков поблизости (требует право)
 * - Частичное совпадение ников
 * - Защита от спама упоминаний
 */
public class MentionHandler {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Паттерны для различных типов упоминаний
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");
    private static final Pattern EVERYONE_PATTERN = Pattern.compile("@(everyone|все|all)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HERE_PATTERN = Pattern.compile("@(here|здесь)", Pattern.CASE_INSENSITIVE);

    // Максимум упоминаний в одном сообщении для предотвращения спама
    private static final int MAX_MENTIONS_PER_MESSAGE = 5;

    public MentionHandler(ChatPlugin plugin, ChatConfig chatConfig) {
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

    /**
     * Обработка упоминаний в сообщении
     */
    public MentionResult processMentions(String message, Player sender) {
        String processedMessage = message;
        Set<Player> mentionedPlayers = new HashSet<>();
        boolean hasEveryoneMention = false;
        boolean hasHereMention = false;

        // Проверка @everyone
        Matcher everyoneMatcher = EVERYONE_PATTERN.matcher(message);
        if (everyoneMatcher.find()) {
            if (sender.hasPermission(chatConfig.getMentionEveryonePermission())) {
                hasEveryoneMention = true;
                String everyoneFormat = chatConfig.getMentionEveryoneFormat();
                processedMessage = everyoneMatcher.replaceAll(everyoneFormat);
                mentionedPlayers.addAll(Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(sender))
                        .collect(Collectors.toList()));
            } else {
                // Не заменяем, если нет прав
                plugin.getLogger().info(sender.getName() + " tried to use @everyone without permission");
            }
        }

        // Проверка @here
        Matcher hereMatcher = HERE_PATTERN.matcher(processedMessage);
        if (hereMatcher.find()) {
            if (sender.hasPermission(chatConfig.getMentionHerePermission())) {
                hasHereMention = true;
                String hereFormat = chatConfig.getMentionHereFormat();
                processedMessage = hereMatcher.replaceAll(hereFormat);

                // Добавляем игроков в радиусе
                int radius = chatConfig.getMentionHereRadius();
                Location senderLoc = sender.getLocation();
                mentionedPlayers.addAll(Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(sender))
                        .filter(p -> p.getWorld().equals(sender.getWorld()))
                        .filter(p -> p.getLocation().distanceSquared(senderLoc) <= radius * radius)
                        .collect(Collectors.toList()));
            } else {
                plugin.getLogger().info(sender.getName() + " tried to use @here without permission");
            }
        }

        // Обработка обычных упоминаний @PlayerName
        if (!hasEveryoneMention && mentionedPlayers.size() < MAX_MENTIONS_PER_MESSAGE) {
            Matcher matcher = MENTION_PATTERN.matcher(processedMessage);
            StringBuffer sb = new StringBuffer();

            while (matcher.find() && mentionedPlayers.size() < MAX_MENTIONS_PER_MESSAGE) {
                String mentionedName = matcher.group(1);

                // Пропускаем, если это уже обработанный @everyone/@here
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

    /**
     * Поиск игрока по нику с поддержкой частичного совпадения
     */
    private Player findPlayer(String name) {
        // Точное совпадение
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }

        // Частичное совпадение (начинается с)
        List<Player> matches = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getName().toLowerCase().startsWith(name.toLowerCase()))
                .collect(Collectors.toList());

        return matches.size() == 1 ? matches.get(0) : null;
    }

    /**
     * Уведомление упомянутых игроков
     */
    public void notifyMentionedPlayers(List<Player> mentionedPlayers, MentionType type) {
        String message = getMentionMessage(type);
        String soundName = chatConfig.getMentionSound();

        for (Player mentionedPlayer : mentionedPlayers) {
            // Отправка звука
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                mentionedPlayer.playSound(mentionedPlayer.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Звук '" + soundName + "' не найден!");
            }

            // Отправка уведомления в action bar
            mentionedPlayer.sendActionBar(miniMessage.deserialize(message));
        }
    }

    /**
     * Получить сообщение уведомления в зависимости от типа упоминания
     */
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

    /**
     * Получить список онлайн игроков для автодополнения
     */
    public List<String> getPlayerSuggestions(String partial) {
        String lower = partial.toLowerCase();
        List<String> suggestions = new ArrayList<>();

        // Добавляем специальные упоминания
        if ("everyone".startsWith(lower) || "все".startsWith(lower)) {
            suggestions.add("everyone");
        }
        if ("here".startsWith(lower) || "здесь".startsWith(lower)) {
            suggestions.add("here");
        }

        // Добавляем ники игроков
        suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .sorted()
                .limit(10)
                .collect(Collectors.toList()));

        return suggestions;
    }

    /**
     * Типы упоминаний
     */
    public enum MentionType {
        NORMAL,     // @PlayerName
        EVERYONE,   // @everyone
        HERE        // @here
    }
}