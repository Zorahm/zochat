package zorahm.zochat.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatPlugin;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class SeenCommand implements CommandExecutor, TabCompleter {
    private final ChatPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    public SeenCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(miniMessage.deserialize(
                    "<yellow>Использование: /seen <игрок></yellow>"));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        // Проверяем, онлайн ли игрок
        if (target.isOnline()) {
            sender.sendMessage(miniMessage.deserialize(
                    "<green>Игрок <white>" + target.getName() + "</white> сейчас онлайн!</green>"));
            return true;
        }

        // Получаем время последней активности из БД асинхронно
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Timestamp lastSeen = plugin.getChatLogger().getPlayerLastSeen(target.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (lastSeen == null) {
                    sender.sendMessage(miniMessage.deserialize(
                            "<red>Информация об игроке <white>" + target.getName() + "</white> не найдена!</red>"));
                    return;
                }

                String formattedDate = dateFormat.format(new Date(lastSeen.getTime()));
                long timeDiff = System.currentTimeMillis() - lastSeen.getTime();
                String timeAgo = formatTimeDifference(timeDiff);

                sender.sendMessage(miniMessage.deserialize(
                        "<gray>Игрок <white>" + target.getName() + "</white> был в сети:</gray>\n" +
                                "<yellow>" + formattedDate + "</yellow> <gray>(" + timeAgo + " назад)</gray>"));
            });
        });

        return true;
    }

    private String formatTimeDifference(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " " + getDeclension(days, "день", "дня", "дней");
        } else if (hours > 0) {
            return hours + " " + getDeclension(hours, "час", "часа", "часов");
        } else if (minutes > 0) {
            return minutes + " " + getDeclension(minutes, "минуту", "минуты", "минут");
        } else {
            return seconds + " " + getDeclension(seconds, "секунду", "секунды", "секунд");
        }
    }

    private String getDeclension(long number, String form1, String form2, String form3) {
        long n = Math.abs(number) % 100;
        long n1 = n % 10;
        if (n > 10 && n < 20) return form3;
        if (n1 > 1 && n1 < 5) return form2;
        if (n1 == 1) return form1;
        return form3;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
