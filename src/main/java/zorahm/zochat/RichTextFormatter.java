package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Класс для обработки rich форматирования текста
 * Поддерживает:
 * - **bold** (жирный текст)
 * - *italic* (курсив)
 * - ~~strikethrough~~ (зачеркнутый)
 */
public class RichTextFormatter {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Паттерны для форматирования (в порядке приоритета)
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(.+?)~~");

    /**
     * Применяет rich форматирование к тексту
     */
    public static String applyFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Сначала обрабатываем жирный текст
        text = processBold(text);

        // Затем курсив
        text = processItalic(text);

        // И наконец зачеркнутый
        text = processStrikethrough(text);

        return text;
    }

    /**
     * Обрабатывает жирный текст **text**
     */
    private static String processBold(String text) {
        Matcher matcher = BOLD_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1);
            matcher.appendReplacement(sb, "<bold>" + Matcher.quoteReplacement(content) + "</bold>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Обрабатывает курсив *text*
     */
    private static String processItalic(String text) {
        Matcher matcher = ITALIC_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1);
            matcher.appendReplacement(sb, "<italic>" + Matcher.quoteReplacement(content) + "</italic>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Обрабатывает зачеркнутый текст ~~text~~
     */
    private static String processStrikethrough(String text) {
        Matcher matcher = STRIKETHROUGH_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1);
            matcher.appendReplacement(sb, "<strikethrough>" + Matcher.quoteReplacement(content) + "</strikethrough>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Применяет форматирование и преобразует в Component
     */
    public static Component formatToComponent(String text) {
        String formatted = applyFormatting(text);
        return miniMessage.deserialize(formatted);
    }
}
