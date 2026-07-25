package zorahm.zochat.presence;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import zorahm.zochat.chat.CooldownService;
import zorahm.zochat.config.ChatConfig;
import zorahm.zochat.config.Messages;
import zorahm.zochat.privatemsg.PrivateMessageService;
import zorahm.zochat.storage.OfflineMessageRepository;
import zorahm.zochat.util.Sounds;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class PresenceListener implements Listener {
    private final Plugin plugin;
    private final ChatConfig config;
    private final Messages messages;
    private final WelcomeMessages welcome;
    private final OfflineMessageRepository offline;
    private final PrivateMessageService pm;
    private final CooldownService cooldowns;

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public PresenceListener(Plugin plugin, ChatConfig config, Messages messages,
                            WelcomeMessages welcome, OfflineMessageRepository offline,
                            PrivateMessageService pm, CooldownService cooldowns) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.welcome = welcome;
        this.offline = offline;
        this.pm = pm;
        this.cooldowns = cooldowns;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);

        if (config.isJoinMessageEnabled() && !player.hasPermission(config.getJoinStealthPermission())) {
            Bukkit.getServer().sendMessage(mm.deserialize(
                    config.getJoinMessageFormat().replace("{player}", player.getName())));
            playToAll(config.getJoinMessageSound());
        }

        if (welcome.isEnabled()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    // The player may have disconnected during the delay — don't message a ghost.
                    if (!player.isOnline()) return;
                    for (Component c : welcome.getWelcomeMessages(player.getName())) {
                        player.sendMessage(c);
                    }
                }
            }.runTaskLater(plugin, welcome.getDelay());
        }

        offline.drainFor(player.getUniqueId(), list -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (list.isEmpty()) return;
            player.sendMessage(mm.deserialize(messages.get("chat.offline-messages-header")
                    .replace("{count}", String.valueOf(list.size()))));
            for (OfflineMessageRepository.OfflineMessage om : list) {
                String senderName = Bukkit.getOfflinePlayer(om.sender()).getName();
                if (senderName == null) senderName = "Unknown";
                String ts = time.format(Instant.ofEpochMilli(om.timestamp().getTime()));
                // Escape the stored sender text — it's the player's literal message and must not be
                // re-parsed as MiniMessage (would let tags injected into a PM render on delivery).
                Component body = mm.deserialize(mm.escapeTags(om.message()))
                        .hoverEvent(HoverEvent.showText(mm.deserialize(
                                messages.get("chat.message-timestamp").replace("{time}", ts))));
                Component line = mm.deserialize(config.getPrivateMessageFormat().replace("{player}", senderName))
                        .replaceText(b -> b.matchLiteral("{message}").replacement(body));
                player.sendMessage(line);
            }
        }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(null);
        if (config.isQuitMessageEnabled() && !player.hasPermission(config.getQuitStealthPermission())) {
            Bukkit.getServer().sendMessage(mm.deserialize(
                    config.getQuitMessageFormat().replace("{player}", player.getName())));
            playToAll(config.getQuitMessageSound());
        }
        cooldowns.clear(player.getUniqueId());
        pm.clearLastMessaged(player.getUniqueId());
    }

    private void playToAll(String soundName) {
        Sounds.parse(soundName).ifPresent(s ->
                Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), s, 1.0f, 1.0f)));
    }
}
