package zorahm.zochat.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import zorahm.zochat.config.ChatConfig;

public final class ChatListener implements Listener {
    private final Plugin plugin;
    private final ChatService chatService;
    private final ChatConfig config;
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    public ChatListener(Plugin plugin, ChatService chatService, ChatConfig config) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String raw = plain.serialize(event.message());
        ChatChannel channel;
        if (raw.startsWith("!")) {
            channel = ChatChannel.GLOBAL;
            raw = raw.substring(1).trim();
        } else {
            channel = config.isLocalChatEnabled() ? ChatChannel.LOCAL : ChatChannel.GLOBAL;
        }
        event.setCancelled(true);

        // AsyncChatEvent fires off the main thread, but the send pipeline reads player state
        // (inventory, health, location) and plays sounds — main-thread-only work. Hop back on.
        Player sender = event.getPlayer();
        String finalRaw = raw;
        ChatChannel finalChannel = channel;
        Bukkit.getScheduler().runTask(plugin, () -> chatService.send(sender, finalRaw, finalChannel));
    }
}
