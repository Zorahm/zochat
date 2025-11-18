package zorahm.zochat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Менеджер для контроля частоты сообщений и защиты от спама
 */
public class RateLimitManager {
    private final ChatPlugin plugin;
    private final ModerationManager moderationManager;

    // Хранит список временных меток последних сообщений для каждого игрока
    private final Map<UUID, LinkedList<Long>> messageTimestamps = new ConcurrentHashMap<>();

    // Счётчик нарушений для каждого игрока
    private final Map<UUID, Integer> violationCount = new ConcurrentHashMap<>();

    // Настройки из конфига
    private int maxMessagesPerWindow;
    private long timeWindowMillis;
    private long tempMuteDurationMillis;
    private int violationsForBan;
    private long banDurationMillis;

    public RateLimitManager(ChatPlugin plugin, ModerationManager moderationManager) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
        loadConfig();
    }

    /**
     * Загружает настройки из конфигурации
     */
    public void loadConfig() {
        this.maxMessagesPerWindow = plugin.getConfig().getInt("rate-limit.max-messages", 3);
        this.timeWindowMillis = plugin.getConfig().getInt("rate-limit.time-window-seconds", 5) * 1000L;
        this.tempMuteDurationMillis = plugin.getConfig().getInt("rate-limit.temp-mute-seconds", 30) * 1000L;
        this.violationsForBan = plugin.getConfig().getInt("rate-limit.violations-for-ban", 3);
        this.banDurationMillis = plugin.getConfig().getInt("rate-limit.ban-duration-hours", 1) * 3600000L;
    }

    /**
     * Проверяет, превысил ли игрок лимит сообщений
     * @return true если игрок превысил лимит
     */
    public boolean checkRateLimit(Player player) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Получаем или создаём список временных меток для игрока
        LinkedList<Long> timestamps = messageTimestamps.computeIfAbsent(playerUUID, k -> new LinkedList<>());

        // Удаляем устаревшие временные метки (за пределами временного окна)
        timestamps.removeIf(timestamp -> (currentTime - timestamp) > timeWindowMillis);

        // Проверяем, превышен ли лимит
        if (timestamps.size() >= maxMessagesPerWindow) {
            handleViolation(player);
            return true;
        }

        // Добавляем текущую временную метку
        timestamps.add(currentTime);
        return false;
    }

    /**
     * Обрабатывает нарушение лимита сообщений
     */
    private void handleViolation(Player player) {
        UUID playerUUID = player.getUniqueId();
        int violations = violationCount.getOrDefault(playerUUID, 0) + 1;
        violationCount.put(playerUUID, violations);

        plugin.getLogger().log(Level.INFO, "Игрок {0} превысил лимит сообщений. Нарушений: {1}",
                new Object[]{player.getName(), violations});

        if (violations >= violationsForBan) {
            // Чат-бан на указанное время
            moderationManager.mutePlayerAsync(
                    playerUUID,
                    UUID.fromString("00000000-0000-0000-0000-000000000000"), // Системный UUID
                    "Многократное превышение лимита сообщений",
                    banDurationMillis
            );

            player.sendMessage("§c§lВы были замьючены на " + (banDurationMillis / 3600000) + " час(а) за спам!");

            // Сбрасываем счётчик нарушений
            violationCount.put(playerUUID, 0);

            plugin.getLogger().log(Level.WARNING, "Игрок {0} получил мьют на {1} часов за многократный спам",
                    new Object[]{player.getName(), banDurationMillis / 3600000});
        } else {
            // Временный мьют
            moderationManager.mutePlayerAsync(
                    playerUUID,
                    UUID.fromString("00000000-0000-0000-0000-000000000000"), // Системный UUID
                    "Превышение лимита сообщений",
                    tempMuteDurationMillis
            );

            player.sendMessage("§e§lВы отправляете сообщения слишком быстро! Мьют на " + (tempMuteDurationMillis / 1000) + " секунд.");
            player.sendMessage("§7Нарушений: " + violations + "/" + violationsForBan + " (при " + violationsForBan + " нарушениях - бан на " + (banDurationMillis / 3600000) + " час)");
        }

        // Очищаем временные метки для игрока
        messageTimestamps.get(playerUUID).clear();
    }

    /**
     * Очищает данные о сообщениях игрока
     */
    public void clearPlayerData(UUID playerUUID) {
        messageTimestamps.remove(playerUUID);
        violationCount.remove(playerUUID);
    }

    /**
     * Очищает счётчик нарушений для игрока
     */
    public void resetViolations(UUID playerUUID) {
        violationCount.put(playerUUID, 0);
    }

    /**
     * Получает количество нарушений для игрока
     */
    public int getViolationCount(UUID playerUUID) {
        return violationCount.getOrDefault(playerUUID, 0);
    }

    /**
     * Периодически очищает старые данные (вызывается по таймеру)
     */
    public void cleanupOldData() {
        long currentTime = System.currentTimeMillis();

        // Очищаем устаревшие временные метки
        messageTimestamps.entrySet().removeIf(entry -> {
            LinkedList<Long> timestamps = entry.getValue();
            timestamps.removeIf(timestamp -> (currentTime - timestamp) > timeWindowMillis * 2);
            return timestamps.isEmpty();
        });

        // Снижаем счётчик нарушений со временем (каждый час снижаем на 1)
        violationCount.entrySet().removeIf(entry -> {
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
            }
            return entry.getValue() <= 0;
        });
    }

    /**
     * Запускает периодическую очистку данных
     */
    public void startCleanupTask() {
        // Очищаем данные каждые 5 минут
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOldData, 6000L, 6000L);
    }
}
