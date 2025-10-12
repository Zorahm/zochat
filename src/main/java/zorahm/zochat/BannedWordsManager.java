package zorahm.zochat;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Улучшенный менеджер фильтрации запрещенных слов
 * Поддерживает:
 * - Точное совпадение
 * - Частичное совпадение в словах
 * - Нормализацию текста (обход через пробелы, повторы, замену символов)
 * - Регулярные выражения
 * - Замену на цензуру или полную блокировку
 */
public class BannedWordsManager {
    private final ChatConfig chatConfig;
    private final Map<String, Character> charReplacements = new HashMap<>();

    // Режимы фильтрации
    public enum FilterMode {
        EXACT,      // Точное совпадение слова
        CONTAINS,   // Слово содержится в сообщении
        SMART       // Умная проверка с нормализацией
    }

    // Действие при обнаружении
    public enum FilterAction {
        BLOCK,      // Заблокировать сообщение
        REPLACE     // Заменить на ***
    }

    public BannedWordsManager(ChatConfig chatConfig) {
        this.chatConfig = chatConfig;
        buildCharReplacements();
    }

    /**
     * Карта замены похожих символов для обхода фильтра
     */
    private void buildCharReplacements() {
        // Русские
        charReplacements.put("а", 'a');
        charReplacements.put("е", 'e');
        charReplacements.put("о", 'o');
        charReplacements.put("р", 'p');
        charReplacements.put("с", 'c');
        charReplacements.put("у", 'y');
        charReplacements.put("х", 'x');

        // L33t speak
        charReplacements.put("4", 'a');
        charReplacements.put("3", 'e');
        charReplacements.put("1", 'i');
        charReplacements.put("0", 'o');
        charReplacements.put("5", 's');
        charReplacements.put("7", 't');
        charReplacements.put("@", 'a');
        charReplacements.put("$", 's');
    }

    /**
     * Проверяет сообщение на запрещенные слова
     */
    public FilterResult checkMessage(String message) {
        if (!chatConfig.isBannedWordsEnabled()) {
            return new FilterResult(false, message, null);
        }

        List<String> bannedWords = chatConfig.getBannedWords();
        if (bannedWords.isEmpty()) {
            return new FilterResult(false, message, null);
        }

        FilterMode mode = getFilterMode();
        FilterAction action = getFilterAction();

        String normalizedMessage = normalize(message);
        String processedMessage = message;

        for (String bannedWord : bannedWords) {
            // Проверка на регулярное выражение
            if (bannedWord.startsWith("regex:")) {
                String regex = bannedWord.substring(6);
                if (matchesRegex(message, regex)) {
                    if (action == FilterAction.BLOCK) {
                        return new FilterResult(true, null, bannedWord);
                    } else {
                        processedMessage = replaceRegex(processedMessage, regex);
                    }
                }
                continue;
            }

            String normalizedBanned = normalize(bannedWord);
            boolean found = false;

            switch (mode) {
                case EXACT:
                    found = normalizedMessage.equals(normalizedBanned);
                    break;

                case CONTAINS:
                    found = normalizedMessage.contains(normalizedBanned);
                    break;

                case SMART:
                    found = smartContains(normalizedMessage, normalizedBanned);
                    break;
            }

            if (found) {
                if (action == FilterAction.BLOCK) {
                    return new FilterResult(true, null, bannedWord);
                } else {
                    processedMessage = censorWord(processedMessage, bannedWord);
                }
            }
        }

        return new FilterResult(action == FilterAction.REPLACE && !processedMessage.equals(message),
                              processedMessage, null);
    }

    /**
     * Нормализация текста для проверки
     */
    private String normalize(String text) {
        String normalized = text.toLowerCase();

        // Удаляем пробелы и спецсимволы между буквами
        normalized = normalized.replaceAll("[\\s_\\-.*+]+", "");

        // Удаляем повторяющиеся символы (привеееет -> привет)
        normalized = normalized.replaceAll("(.)\\1{2,}", "$1");

        // Заменяем похожие символы
        StringBuilder sb = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            String key = String.valueOf(c);
            Character replacement = charReplacements.get(key);
            sb.append(replacement != null ? replacement : c);
        }

        return sb.toString();
    }

    /**
     * Умная проверка содержания с учетом границ слов
     */
    private boolean smartContains(String message, String bannedWord) {
        if (!message.contains(bannedWord)) {
            return false;
        }

        // Проверяем, что запрещенное слово - это отдельное слово, а не часть другого
        String regex = ".*\\b" + Pattern.quote(bannedWord) + "\\b.*";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(message).matches();
    }

    /**
     * Проверка по регулярному выражению
     */
    private boolean matchesRegex(String message, String regex) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(message).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /**
     * Замена по регулярному выражению
     */
    private String replaceRegex(String message, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            return pattern.matcher(message).replaceAll("***");
        } catch (PatternSyntaxException e) {
            return message;
        }
    }

    /**
     * Цензура слова в сообщении
     */
    private String censorWord(String message, String bannedWord) {
        String censor = "*".repeat(Math.max(3, bannedWord.length()));
        return message.replaceAll("(?i)" + Pattern.quote(bannedWord), censor);
    }

    private FilterMode getFilterMode() {
        String mode = chatConfig.getBannedWordsMode();
        try {
            return FilterMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FilterMode.SMART;
        }
    }

    private FilterAction getFilterAction() {
        String action = chatConfig.getBannedWordsAction();
        try {
            return FilterAction.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FilterAction.BLOCK;
        }
    }

    /**
     * Результат проверки сообщения
     */
    public static class FilterResult {
        private final boolean blocked;
        private final String processedMessage;
        private final String matchedWord;

        public FilterResult(boolean blocked, String processedMessage, String matchedWord) {
            this.blocked = blocked;
            this.processedMessage = processedMessage;
            this.matchedWord = matchedWord;
        }

        public boolean isBlocked() {
            return blocked;
        }

        public String getProcessedMessage() {
            return processedMessage;
        }

        public String getMatchedWord() {
            return matchedWord;
        }
    }
}
