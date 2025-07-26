package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class ColorUtil {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    /**
     * Преобразует текст в Component с использованием MiniMessage.
     *
     * @param message Сообщение в формате MiniMessage.
     * @return Component для отображения.
     */
    public static Component colorize(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return miniMessage.deserialize(message);
    }
}
