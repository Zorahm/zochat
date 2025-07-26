package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class MessageUtil {
    public static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final String PREFIX = "<#d45079>ChatPlugin</#d45079> <#c0c0c0>•</#c0c0c0> ";

    /**
     * Создаёт сообщение с единым префиксом.
     *
     * @param message Основное сообщение.
     * @return Компонент с оформлением.
     */
    public static Component styledMessage(String message) {
        return miniMessage.deserialize(PREFIX + message);
    }

    /**
     * Создаёт сообщение только с текстом, без префикса.
     *
     * @param message Основное сообщение.
     * @return Компонент без префикса.
     */
    public static Component plainMessage(String message) {
        return miniMessage.deserialize(message);
    }
}
