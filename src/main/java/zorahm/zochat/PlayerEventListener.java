package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

public class PlayerEventListener implements Listener {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainTextSerializer = PlainTextComponentSerializer.plainText();

    public PlayerEventListener(ChatPlugin plugin, ChatConfig chatConfig) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (chatConfig.isJoinMessageEnabled() && !player.hasPermission(chatConfig.getJoinStealthPermission())) {
            Component message = miniMessage.deserialize(
                    chatConfig.getJoinMessageFormat().replace("{player}", player.getName())
            );
            Bukkit.getServer().sendMessage(message);
            String sound = chatConfig.getJoinMessageSound();
            if (sound != null && !sound.isEmpty()) {
                try {
                    Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.valueOf(sound), 1.0f, 1.0f));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Неверный звук для join-message: " + sound);
                }
            }
            plugin.logStandard(Level.INFO, "player-joined", player.getName());
        } else {
            plugin.logDebug(Level.INFO, "player-join-hidden", player.getName());
        }
        // Отключаем стандартное сообщение о входе
        event.setJoinMessage(null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (chatConfig.isQuitMessageEnabled() && !player.hasPermission(chatConfig.getQuitStealthPermission())) {
            Component message = miniMessage.deserialize(
                    chatConfig.getQuitMessageFormat().replace("{player}", player.getName())
            );
            Bukkit.getServer().sendMessage(message);
            String sound = chatConfig.getQuitMessageSound();
            if (sound != null && !sound.isEmpty()) {
                try {
                    Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.valueOf(sound), 1.0f, 1.0f));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Неверный звук для quit-message: " + sound);
                }
            }
            plugin.logStandard(Level.INFO, "player-quit", player.getName());
        } else {
            plugin.logDebug(Level.INFO, "player-quit-hidden", player.getName());
        }
        // Отключаем стандартное сообщение о выходе
        event.setQuitMessage(null);
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!chatConfig.isAdvancementMessageEnabled()) {
            return;
        }
        // Проверяем, что это не "рецепт" или другое незначительное достижение
        if (event.getAdvancement().getKey().getKey().startsWith("recipes/")) {
            return;
        }
        Player player = event.getPlayer();
        String advancementName = event.getAdvancement().getDisplay() != null
                ? plainTextSerializer.serialize(event.getAdvancement().getDisplay().title())
                : event.getAdvancement().getKey().getKey();
        Component message = miniMessage.deserialize(
                chatConfig.getAdvancementMessageFormat()
                        .replace("{player}", player.getName())
                        .replace("{advancement}", advancementName)
        );
        Bukkit.getServer().sendMessage(message);
        String sound = chatConfig.getAdvancementMessageSound();
        if (sound != null && !sound.isEmpty()) {
            try {
                Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.valueOf(sound), 1.0f, 1.0f));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный звук для advancement-message: " + sound);
            }
        }
        plugin.logStandard(Level.INFO, "player-advancement", player.getName(), advancementName);
    }
}