package zorahm.zochat.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatPlugin;
import zorahm.zochat.IgnoreManager;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class IgnoreListCommand implements CommandExecutor {
    private final ChatPlugin plugin;
    private final IgnoreManager ignoreManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public IgnoreListCommand(ChatPlugin plugin, IgnoreManager ignoreManager) {
        this.plugin = plugin;
        this.ignoreManager = ignoreManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Эта команда доступна только для игроков!");
            return true;
        }

        Player player = (Player) sender;
        Set<UUID> ignoredPlayers = ignoreManager.getIgnoredPlayers(player.getUniqueId());

        if (ignoredPlayers.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(
                    "<yellow>Вы никого не игнорируете!</yellow>"));
            return true;
        }

        player.sendMessage(miniMessage.deserialize(
                "<gradient:#f6a0d3:#b47ee5>═══════ Игнорируемые игроки ═══════</gradient>"));

        String ignoredList = ignoredPlayers.stream()
                .map(Bukkit::getOfflinePlayer)
                .map(OfflinePlayer::getName)
                .filter(name -> name != null)
                .collect(Collectors.joining(", "));

        player.sendMessage(miniMessage.deserialize(
                "<gray>Всего: <white>" + ignoredPlayers.size() + "</white></gray>\n" +
                        "<white>" + ignoredList + "</white>"));

        player.sendMessage(miniMessage.deserialize(
                "<gradient:#f6a0d3:#b47ee5>═══════════════════════════════════</gradient>"));

        return true;
    }
}
