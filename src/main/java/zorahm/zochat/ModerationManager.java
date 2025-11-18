package zorahm.zochat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import zorahm.zochat.database.ChatLogger;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Менеджер модерации для управления мьютами, варнами и чат-банами
 */
public class ModerationManager {
    private final ChatPlugin plugin;
    private final Connection connection;
    private final Map<UUID, MuteInfo> activeMutes = new HashMap<>();
    private final Map<UUID, Integer> chatBans = new HashMap<>();

    public ModerationManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.connection = plugin.getChatLogger().getConnection();
        loadActiveMutes();
        loadChatBans();
    }

    // ═══════════════════════════════════════════════════════════════════
    // МЬЮТЫ
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Асинхронно мьютит игрока
     */
    public void mutePlayerAsync(UUID playerUUID, UUID mutedBy, String reason, long durationMillis) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            LocalDateTime endTime = durationMillis > 0 ?
                    LocalDateTime.now().plusSeconds(durationMillis / 1000) : null;

            String sql = "INSERT INTO mutes (player_uuid, muted_by, reason, end_time) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, mutedBy.toString());
                stmt.setString(3, reason != null ? reason : "Не указана");
                if (endTime != null) {
                    stmt.setTimestamp(4, Timestamp.valueOf(endTime));
                } else {
                    stmt.setNull(4, Types.TIMESTAMP);
                }
                stmt.executeUpdate();

                // Кэшируем мьют
                activeMutes.put(playerUUID, new MuteInfo(mutedBy, reason, endTime));

                plugin.getLogger().log(Level.INFO, "Игрок {0} был замьючен модератором {1}",
                        new Object[]{playerUUID, mutedBy});
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось замьютить игрока!", e);
            }
        });
    }

    /**
     * Асинхронно размьючивает игрока
     */
    public void unmutePlayerAsync(UUID playerUUID) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "UPDATE mutes SET active = 0 WHERE player_uuid = ? AND active = 1";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.executeUpdate();

                // Удаляем из кэша
                activeMutes.remove(playerUUID);

                plugin.getLogger().log(Level.INFO, "Игрок {0} был размьючен", playerUUID);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось размьютить игрока!", e);
            }
        });
    }

    /**
     * Проверяет, замьючен ли игрок
     */
    public boolean isMuted(UUID playerUUID) {
        MuteInfo muteInfo = activeMutes.get(playerUUID);
        if (muteInfo == null) {
            return false;
        }

        // Проверяем, не истёк ли мьют
        if (muteInfo.endTime != null && LocalDateTime.now().isAfter(muteInfo.endTime)) {
            unmutePlayerAsync(playerUUID);
            return false;
        }

        return true;
    }

    /**
     * Получает информацию о мьюте
     */
    public MuteInfo getMuteInfo(UUID playerUUID) {
        return activeMutes.get(playerUUID);
    }

    /**
     * Загружает активные мьюты из БД в кэш
     */
    private void loadActiveMutes() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "SELECT player_uuid, muted_by, reason, end_time FROM mutes WHERE active = 1";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    UUID mutedBy = UUID.fromString(rs.getString("muted_by"));
                    String reason = rs.getString("reason");
                    Timestamp endTimeStamp = rs.getTimestamp("end_time");
                    LocalDateTime endTime = endTimeStamp != null ? endTimeStamp.toLocalDateTime() : null;

                    // Проверяем, не истёк ли мьют
                    if (endTime == null || LocalDateTime.now().isBefore(endTime)) {
                        activeMutes.put(playerUUID, new MuteInfo(mutedBy, reason, endTime));
                    } else {
                        // Мьют истёк - деактивируем
                        unmutePlayerAsync(playerUUID);
                    }
                }
                plugin.getLogger().log(Level.INFO, "Загружено {0} активных мьютов", activeMutes.size());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить активные мьюты!", e);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // ВАРНЫ
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Асинхронно выдаёт варнинг игроку
     */
    public void warnPlayerAsync(UUID playerUUID, UUID warnedBy, String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO warnings (player_uuid, warned_by, reason) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, warnedBy.toString());
                stmt.setString(3, reason != null ? reason : "Не указана");
                stmt.executeUpdate();

                plugin.getLogger().log(Level.INFO, "Игрок {0} получил варнинг от {1}",
                        new Object[]{playerUUID, warnedBy});
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось выдать варнинг!", e);
            }
        });
    }

    /**
     * Получает количество варнингов игрока
     */
    public int getWarningCount(UUID playerUUID) {
        String sql = "SELECT COUNT(*) as count FROM warnings WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось получить количество варнингов!", e);
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ЧАТ-БАНЫ
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Асинхронно банит игрока в чате
     */
    public void chatBanPlayerAsync(UUID playerUUID, UUID bannedBy, String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO chat_bans (player_uuid, banned_by, reason) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, bannedBy.toString());
                stmt.setString(3, reason != null ? reason : "Не указана");
                stmt.executeUpdate();

                // Кэшируем бан
                chatBans.put(playerUUID, 1);

                plugin.getLogger().log(Level.INFO, "Игрок {0} получил чат-бан от {1}",
                        new Object[]{playerUUID, bannedBy});
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось забанить игрока в чате!", e);
            }
        });
    }

    /**
     * Асинхронно снимает чат-бан с игрока
     */
    public void unchatBanPlayerAsync(UUID playerUUID) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "UPDATE chat_bans SET active = 0 WHERE player_uuid = ? AND active = 1";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.executeUpdate();

                // Удаляем из кэша
                chatBans.remove(playerUUID);

                plugin.getLogger().log(Level.INFO, "С игрока {0} снят чат-бан", playerUUID);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось снять чат-бан!", e);
            }
        });
    }

    /**
     * Проверяет, забанен ли игрок в чате
     */
    public boolean isChatBanned(UUID playerUUID) {
        return chatBans.containsKey(playerUUID);
    }

    /**
     * Загружает активные чат-баны из БД в кэш
     */
    private void loadChatBans() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "SELECT player_uuid FROM chat_bans WHERE active = 1";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    chatBans.put(playerUUID, 1);
                }
                plugin.getLogger().log(Level.INFO, "Загружено {0} активных чат-банов", chatBans.size());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить активные чат-баны!", e);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Класс для хранения информации о мьюте
     */
    public static class MuteInfo {
        private final UUID mutedBy;
        private final String reason;
        private final LocalDateTime endTime;

        public MuteInfo(UUID mutedBy, String reason, LocalDateTime endTime) {
            this.mutedBy = mutedBy;
            this.reason = reason;
            this.endTime = endTime;
        }

        public UUID getMutedBy() {
            return mutedBy;
        }

        public String getReason() {
            return reason;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public boolean isPermanent() {
            return endTime == null;
        }
    }
}
