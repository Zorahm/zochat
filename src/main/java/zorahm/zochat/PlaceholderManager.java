package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

public class PlaceholderManager {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DateTimeFormatter realtimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Map<String, String> aliasToPlaceholder = new HashMap<>();

    public PlaceholderManager(ChatPlugin plugin, ChatConfig chatConfig) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        buildAliasMap();
    }

    private void buildAliasMap() {
        aliasToPlaceholder.clear();
        List<String> placeholders = Arrays.asList("loc", "world", "time", "health", "ping", "biome", "item");

        for (String placeholder : placeholders) {
            // Основное имя
            aliasToPlaceholder.put(placeholder, placeholder);

            // Алиасы
            List<String> aliases = chatConfig.getPlaceholderAliases(placeholder);
            for (String alias : aliases) {
                aliasToPlaceholder.put(alias.toLowerCase(), placeholder);
            }
        }

        plugin.getLogger().log(Level.INFO, "Built alias map with {0} entries", aliasToPlaceholder.size());
    }

    public Component processPlaceholders(Player player, String message) {
        plugin.getLogger().log(Level.INFO, "Starting placeholder processing for {0}: {1}", new Object[]{player.getName(), message});

        if (!chatConfig.isPlaceholdersEnabled()) {
            plugin.getLogger().log(Level.INFO, "Placeholders disabled globally for {0}", new Object[]{player.getName()});
            return miniMessage.deserialize(message);
        }

        String processedMessage = message;
        Map<String, String> replacements = new HashMap<>();

        // Найти все плейсхолдеры в сообщении (^word)
        for (Map.Entry<String, String> entry : aliasToPlaceholder.entrySet()) {
            String aliasOrName = entry.getKey();
            String placeholderName = entry.getValue();
            String pattern = "^" + aliasOrName;

            if (processedMessage.contains(pattern)) {
                // Проверяем права и enabled
                if (!chatConfig.isPlaceholderEnabled(placeholderName)) {
                    continue;
                }
                if (!player.hasPermission(chatConfig.getPlaceholderPermission(placeholderName))) {
                    continue;
                }

                String value = processPlaceholder(player, placeholderName);
                if (value != null) {
                    replacements.put(pattern, value);
                    plugin.getLogger().log(Level.INFO, "Processed ^{0} for {1}: {2}",
                            new Object[]{aliasOrName, player.getName(), value});
                }
            }
        }

        // Замена плейсхолдеров
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            processedMessage = processedMessage.replace(entry.getKey(), entry.getValue());
        }

        plugin.getLogger().log(Level.INFO, "Final processed message for {0}: {1}", new Object[]{player.getName(), processedMessage});
        return miniMessage.deserialize(processedMessage);
    }

    private String processPlaceholder(Player player, String placeholderName) {
        String format = chatConfig.getPlaceholderFormat(placeholderName);

        switch (placeholderName) {
            case "loc":
                return format
                        .replace("{x}", String.format("%.0f", player.getLocation().getX()))
                        .replace("{y}", String.format("%.0f", player.getLocation().getY()))
                        .replace("{z}", String.format("%.0f", player.getLocation().getZ()))
                        .replace("{world}", player.getWorld().getName());

            case "world":
                return format.replace("{world}", player.getWorld().getName());

            case "time":
                long worldTime = player.getWorld().getTime();
                long hours = (worldTime / 1000 + 6) % 24;
                long minutes = (worldTime % 1000) * 60 / 1000;
                String gameTime = String.format("%02d:%02d", hours, minutes);
                String realTime = realtimeFormatter.format(Instant.now());
                return format
                        .replace("{time}", gameTime)
                        .replace("{realtime}", realTime);

            case "health":
                double health = player.getHealth();
                double maxHealth = player.getMaxHealth();
                return format
                        .replace("{health}", String.format("%.1f", health))
                        .replace("{maxhealth}", String.format("%.1f", maxHealth));

            case "ping":
                int ping = player.getPing();
                return format.replace("{ping}", String.valueOf(ping));

            case "biome":
                String biome = player.getLocation().getBlock().getBiome().name()
                        .toLowerCase()
                        .replace("_", " ");
                return format.replace("{biome}", biome);

            case "item":
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getType().isAir()) {
                    return format
                            .replace("{item}", "пусто")
                            .replace("{amount}", "0");
                }
                String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? item.getItemMeta().getDisplayName()
                        : item.getType().name().toLowerCase().replace("_", " ");
                return format
                        .replace("{item}", itemName)
                        .replace("{amount}", String.valueOf(item.getAmount()));

            default:
                return null;
        }
    }
}