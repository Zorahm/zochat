package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class PlaceholderManager {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public PlaceholderManager(ChatPlugin plugin, ChatConfig chatConfig) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
    }

    public Component processPlaceholders(Player player, String message) {
        plugin.getLogger().log(Level.INFO, "Starting placeholder processing for {0}: {1}", new Object[]{player.getName(), message});

        if (!chatConfig.isPlaceholdersEnabled()) {
            plugin.getLogger().log(Level.INFO, "Placeholders disabled globally for {0}", new Object[]{player.getName()});
            return miniMessage.deserialize(message);
        }

        String processedMessage = message;
        Map<String, String> placeholderMap = new HashMap<>();

        // Координаты
        if (chatConfig.isPlaceholderEnabled("loc") && player.hasPermission(chatConfig.getPlaceholderPermission("loc"))) {
            String format = chatConfig.getPlaceholderFormat("loc");
            String locValue = format
                    .replace("{x}", String.format("%.0f", player.getLocation().getX()))
                    .replace("{y}", String.format("%.0f", player.getLocation().getY()))
                    .replace("{z}", String.format("%.0f", player.getLocation().getZ()));
            placeholderMap.put("^loc", locValue);
            plugin.getLogger().log(Level.INFO, "Processed ^loc for {0}: {1}", new Object[]{player.getName(), locValue});
        } else {
            plugin.getLogger().log(Level.INFO, "Skipped ^loc for {0}: enabled={1}, hasPermission={2}",
                    new Object[]{player.getName(), chatConfig.isPlaceholderEnabled("loc"), player.hasPermission(chatConfig.getPlaceholderPermission("loc"))});
        }

        // Здоровье
        if (chatConfig.isPlaceholderEnabled("health") && player.hasPermission(chatConfig.getPlaceholderPermission("health"))) {
            String format = chatConfig.getPlaceholderFormat("health");
            String healthValue = format.replace("{health}", String.format("%.1f", player.getHealth()));
            placeholderMap.put("^health", healthValue);
            plugin.getLogger().log(Level.INFO, "Processed ^health for {0}: {1}", new Object[]{player.getName(), healthValue});
        } else {
            plugin.getLogger().log(Level.INFO, "Skipped ^health for {0}: enabled={1}, hasPermission={2}",
                    new Object[]{player.getName(), chatConfig.isPlaceholderEnabled("health"), player.hasPermission(chatConfig.getPlaceholderPermission("health"))});
        }

        // Замена плейсхолдеров
        for (Map.Entry<String, String> entry : placeholderMap.entrySet()) {
            processedMessage = processedMessage.replace(entry.getKey(), entry.getValue());
        }

        plugin.getLogger().log(Level.INFO, "Final processed message for {0}: {1}", new Object[]{player.getName(), processedMessage});
        return miniMessage.deserialize(processedMessage);
    }
}