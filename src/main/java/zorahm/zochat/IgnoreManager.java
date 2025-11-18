package zorahm.zochat;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Менеджер системы игнорирования игроков
 */
public class IgnoreManager {
    private final ChatPlugin plugin;
    private final File ignoreFile;
    private FileConfiguration ignoreConfig;
    private final Map<UUID, Set<UUID>> ignoreMap = new HashMap<>();

    public IgnoreManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.ignoreFile = new File(plugin.getDataFolder(), "ignores.yml");
        loadIgnores();
    }

    /**
     * Загружает список игнорируемых из файла
     */
    private void loadIgnores() {
        if (!ignoreFile.exists()) {
            try {
                ignoreFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось создать файл ignores.yml!", e);
                return;
            }
        }

        ignoreConfig = YamlConfiguration.loadConfiguration(ignoreFile);
        ignoreMap.clear();

        for (String key : ignoreConfig.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(key);
                List<String> ignoredList = ignoreConfig.getStringList(key);
                Set<UUID> ignoredUUIDs = new HashSet<>();

                for (String ignored : ignoredList) {
                    try {
                        ignoredUUIDs.add(UUID.fromString(ignored));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().log(Level.WARNING, "Неверный UUID в списке игнорирования: " + ignored);
                    }
                }

                ignoreMap.put(playerUUID, ignoredUUIDs);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Неверный UUID игрока в списке игнорирования: " + key);
            }
        }

        plugin.getLogger().log(Level.INFO, "Загружено {0} списков игнорирования", ignoreMap.size());
    }

    /**
     * Сохраняет список игнорируемых в файл асинхронно
     */
    private void saveIgnoresAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Map.Entry<UUID, Set<UUID>> entry : ignoreMap.entrySet()) {
                List<String> ignoredList = new ArrayList<>();
                for (UUID ignored : entry.getValue()) {
                    ignoredList.add(ignored.toString());
                }
                ignoreConfig.set(entry.getKey().toString(), ignoredList);
            }

            try {
                ignoreConfig.save(ignoreFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить файл ignores.yml!", e);
            }
        });
    }

    /**
     * Добавляет игрока в список игнорируемых
     */
    public void addIgnore(UUID player, UUID toIgnore) {
        ignoreMap.computeIfAbsent(player, k -> new HashSet<>()).add(toIgnore);
        saveIgnoresAsync();
    }

    /**
     * Удаляет игрока из списка игнорируемых
     */
    public void removeIgnore(UUID player, UUID toUnignore) {
        Set<UUID> ignored = ignoreMap.get(player);
        if (ignored != null) {
            ignored.remove(toUnignore);
            if (ignored.isEmpty()) {
                ignoreMap.remove(player);
                ignoreConfig.set(player.toString(), null);
            }
            saveIgnoresAsync();
        }
    }

    /**
     * Проверяет, игнорирует ли игрок другого игрока
     */
    public boolean isIgnoring(UUID player, UUID target) {
        Set<UUID> ignored = ignoreMap.get(player);
        return ignored != null && ignored.contains(target);
    }

    /**
     * Получает список игнорируемых игроков
     */
    public Set<UUID> getIgnoredPlayers(UUID player) {
        return ignoreMap.getOrDefault(player, new HashSet<>());
    }

    /**
     * Очищает весь список игнорируемых для игрока
     */
    public void clearIgnores(UUID player) {
        ignoreMap.remove(player);
        ignoreConfig.set(player.toString(), null);
        saveIgnoresAsync();
    }
}
