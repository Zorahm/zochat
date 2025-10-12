package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.*;

public class LocalChatCommand implements CommandExecutor {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final MessageManager messageManager;
    private final MentionHandler mentionHandler;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LocalChatCommand(ChatPlugin plugin, ChatConfig chatConfig, MessageManager messageManager) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.messageManager = messageManager;
        this.mentionHandler = new MentionHandler(plugin, chatConfig);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("errors.only-players")));
            return true;
        }

        Player player = (Player) sender;

        // Проверяем, включён ли локальный чат
        if (!chatConfig.isLocalChatEnabled()) {
            player.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.local-disabled")));
            return true;
        }

        // Проверяем, указано ли сообщение
        if (args.length == 0) {
            player.sendMessage(miniMessage.deserialize("<red>Использование: /l <сообщение></red>"));
            return true;
        }

        // Формируем сообщение
        String message = String.join(" ", args);

        // Проверка запрещённых слов через улучшенный фильтр
        BannedWordsManager.FilterResult filterResult = plugin.getBannedWordsManager().checkMessage(message);
        if (filterResult.isBlocked()) {
            player.sendMessage(miniMessage.deserialize(messageManager.getMessage("chat.banned-word")));
            return true;
        }
        // Если режим замены - используем отфильтрованное сообщение
        if (filterResult.getProcessedMessage() != null) {
            message = filterResult.getProcessedMessage();
        }

        // Обработка упоминаний
        MentionHandler.MentionResult mentionResult = mentionHandler.processMentions(message, player);
        String processedMessage = mentionResult.getProcessedMessage();

        // Определяем тип упоминания
        MentionHandler.MentionType mentionType = MentionHandler.MentionType.NORMAL;
        if (mentionResult.hasEveryoneMention()) {
            mentionType = MentionHandler.MentionType.EVERYONE;
        } else if (mentionResult.hasHereMention()) {
            mentionType = MentionHandler.MentionType.HERE;
        }

        mentionHandler.notifyMentionedPlayers(mentionResult.getMentionedPlayers(), mentionType);

        // Получаем префикс и суффикс из LuckPerms
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        String prefix = (user != null && user.getCachedData().getMetaData().getPrefix() != null)
                ? user.getCachedData().getMetaData().getPrefix() : "";
        String suffix = (user != null && user.getCachedData().getMetaData().getSuffix() != null)
                ? user.getCachedData().getMetaData().getSuffix() : "";

        // Форматируем сообщение
        String format = chatConfig.getLocalChatFormat();
        String formattedMessage = format
                .replace("{prefix}", prefix)
                .replace("{player}", player.getName())
                .replace("{suffix}", suffix)
                .replace("{message}", processedMessage);

        Component parsedMessage = miniMessage.deserialize(formattedMessage);

        // Отправляем сообщение в локальный чат
        int radius = chatConfig.getLocalChatRadius();
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.getWorld().equals(player.getWorld()) &&
                    recipient.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                recipient.sendMessage(parsedMessage);
            }
        }

        return true;
    }
}